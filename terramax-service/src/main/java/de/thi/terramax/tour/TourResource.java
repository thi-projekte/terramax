package de.thi.terramax.tour;

import de.thi.terramax.common.ApiErrors;
import de.thi.terramax.common.ErrorResponse;
import de.thi.terramax.common.TerraMaxApplication;
import de.thi.terramax.customer.Customer;
import de.thi.terramax.dealer.Dealer;
import de.thi.terramax.dealer.DealerEmployee;
import de.thi.terramax.security.CurrentUser;
import de.thi.terramax.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Besuchstouren. Sichtbarkeit siehe {@link TourAccess}.
 */
@Path("/tours")
@RolesAllowed({ Roles.SALES_PROFESSIONAL, Roles.DEALER_PRINCIPAL })
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Touren")
@SecurityRequirement(name = TerraMaxApplication.BEARER_SCHEME)
@APIResponse(responseCode = "401", description = "Kein oder ungültiger Token")
@APIResponse(responseCode = "403", description = "Keine TerraMax-Rolle oder kein dealer_id im Token", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
public class TourResource {

    @Inject
    CurrentUser user;

    @Inject
    TourAccess access;

    @GET
    @Operation(summary = "Touren auflisten", description = "dealer_principal: alle Touren des Händlers. "
            + "sales_professional: nur eigene Touren. Neueste zuerst.")
    @APIResponse(responseCode = "200", description = "Touren", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourSummaryDto.class, type = SchemaType.ARRAY)))
    public List<TourSummaryDto> list() {
        return access.visibleTours().stream().map(TourSummaryDto::from).toList();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Eine Tour abrufen", description = "Mit Kunden in Besuchsreihenfolge.")
    @APIResponse(responseCode = "200", description = "Tour", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourDto.class)))
    @APIResponse(responseCode = "404", description = "Unbekannte oder nicht sichtbare Tour", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public TourDto get(@Parameter(description = "ID der Tour", example = "1") @PathParam("id") long id) {
        return TourDto.from(access.visibleTour(id));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Tour anlegen", description = "Legt eine Tour im Status planned an. Der angemeldete Nutzer "
            + "wird als Fahrer eingetragen; ein sales_professional braucht dafür einen Mitarbeiter-Eintrag.")
    @APIResponse(responseCode = "201", description = "Angelegt", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourDto.class)))
    @APIResponse(responseCode = "400", description = "Ungültige Angaben, z. B. Kunde eines anderen Händlers", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response create(TourRequests.Create request, @Context UriInfo uriInfo) {
        String dealerId = user.requireDealerId();
        if (request == null || request.tourDate() == null) {
            throw ApiErrors.badRequest("Das Datum (tourDate) fehlt.");
        }
        Optional<DealerEmployee> self = user.employee();
        if (self.isEmpty() && !user.isDealerPrincipal()) {
            throw ApiErrors.forbidden("Für diesen Nutzer gibt es keinen Mitarbeiter-Eintrag beim Händler.");
        }

        Tour tour = new Tour();
        tour.dealer = Dealer.findById(dealerId);
        if (tour.dealer == null) {
            throw ApiErrors.notFound("Unbekannter Händler: " + dealerId);
        }
        tour.tourDate = request.tourDate();
        self.ifPresent(tour.employees::add);
        for (Long employeeId : nullToEmpty(request.employeeIds())) {
            DealerEmployee employee = DealerEmployee.findById(employeeId);
            if (employee == null || !employee.dealer.id.equals(dealerId)) {
                throw ApiErrors.badRequest("Unbekannter Mitarbeiter: " + employeeId);
            }
            tour.employees.add(employee);
        }
        List<Long> customerIds = nullToEmpty(request.customerIds());
        requireDistinct(customerIds);
        int position = 1;
        for (Long customerId : customerIds) {
            Customer customer = Customer.findForDealer(customerId, dealerId)
                    .orElseThrow(() -> ApiErrors.badRequest("Unbekannter Kunde: " + customerId));
            tour.stops.add(TourCustomer.of(tour, customer, position++));
        }
        tour.persist();
        return Response.created(uriInfo.getAbsolutePathBuilder().path(String.valueOf(tour.id)).build())
                .entity(TourDto.from(tour)).build();
    }

    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Tour ändern", description = "Status, Reihenfolge der Kunden und Besuchsstatus ändern.")
    @APIResponse(responseCode = "200", description = "Geändert", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourDto.class)))
    @APIResponse(responseCode = "400", description = "Ungültige Angaben", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Unbekannte oder nicht sichtbare Tour", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public TourDto patch(@Parameter(description = "ID der Tour", example = "1") @PathParam("id") long id,
            TourRequests.Patch request) {
        if (request == null) {
            throw ApiErrors.badRequest("Die Angaben fehlen.");
        }
        Tour tour = access.visibleTour(id);
        Map<Long, TourCustomer> stops = tour.stops.stream()
                .collect(Collectors.toMap(s -> s.customer.id, Function.identity()));

        if (request.status() != null) {
            try {
                tour.status = TourStatus.fromDbValue(request.status());
            } catch (IllegalArgumentException e) {
                throw ApiErrors.badRequest(e.getMessage());
            }
        }
        if (request.customerOrder() != null) {
            requireDistinct(request.customerOrder());
            if (!new HashSet<>(request.customerOrder()).equals(stops.keySet())) {
                throw ApiErrors.badRequest("customerOrder muss alle Kunden der Tour genau einmal enthalten.");
            }
            int position = 1;
            for (Long customerId : request.customerOrder()) {
                stops.get(customerId).position = position++;
            }
            tour.stops.sort((a, b) -> Integer.compare(a.position, b.position));
        }
        if (request.visited() != null) {
            for (Map.Entry<Long, Boolean> entry : request.visited().entrySet()) {
                TourCustomer stop = stops.get(entry.getKey());
                if (stop == null || entry.getValue() == null) {
                    throw ApiErrors.badRequest("Kunde nicht auf der Tour: " + entry.getKey());
                }
                stop.visited = entry.getValue();
            }
        }
        return TourDto.from(tour);
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static void requireDistinct(List<Long> ids) {
        Set<Long> seen = new HashSet<>();
        for (Long id : ids) {
            if (id == null || !seen.add(id)) {
                throw ApiErrors.badRequest("Kunden-IDs müssen eindeutig sein: " + id);
            }
        }
    }
}
