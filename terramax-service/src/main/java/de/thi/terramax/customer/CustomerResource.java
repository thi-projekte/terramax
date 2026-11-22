package de.thi.terramax.customer;

import de.thi.terramax.common.ApiErrors;
import de.thi.terramax.common.ErrorResponse;
import de.thi.terramax.common.TerraMaxApplication;
import de.thi.terramax.dealer.Dealer;
import de.thi.terramax.security.CurrentUser;
import de.thi.terramax.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;
import java.util.List;
import java.util.Objects;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Kunden des eigenen Händlers. Beide Rollen sehen alle Kunden ihres Händlers, nie die anderer Händler.
 */
@Path("/customers")
@RolesAllowed({ Roles.SALES_PROFESSIONAL, Roles.DEALER_PRINCIPAL })
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Kunden")
@SecurityRequirement(name = TerraMaxApplication.BEARER_SCHEME)
@APIResponse(responseCode = "401", description = "Kein oder ungültiger Token")
@APIResponse(responseCode = "403", description = "Keine TerraMax-Rolle oder kein dealer_id im Token", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
public class CustomerResource {

    public static final String GEO_JSON = "application/geo+json";

    @Inject
    CurrentUser user;

    @GET
    @Operation(summary = "Kunden auflisten", description = "Alle Kunden des eigenen Händlers, sortiert nach Name. "
            + "Mit Accept: application/geo+json als FeatureCollection.")
    @APIResponse(responseCode = "200", description = "Kunden", content = {
            @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CustomerDto.class, type = SchemaType.ARRAY)),
            @Content(mediaType = GEO_JSON, schema = @Schema(implementation = CustomerGeoJson.FeatureCollection.class)) })
    public List<CustomerDto> list() {
        return Customer.listByDealer(user.requireDealerId()).stream().map(CustomerDto::from).toList();
    }

    @GET
    @Produces(GEO_JSON)
    @Operation(hidden = true)
    public CustomerGeoJson.FeatureCollection listAsGeoJson() {
        return CustomerGeoJson.of(Customer.listByDealer(user.requireDealerId()));
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Einen Kunden abrufen")
    @APIResponse(responseCode = "200", description = "Kunde", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CustomerDto.class)))
    @APIResponse(responseCode = "404", description = "Unbekannter Kunde oder Kunde eines anderen Händlers", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public CustomerDto get(@Parameter(description = "ID des Kunden", example = "1") @PathParam("id") long id) {
        return CustomerDto.from(find(id));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Kunden anlegen", description = "Legt einen Kunden für den eigenen Händler an.")
    @APIResponse(responseCode = "201", description = "Angelegt", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CustomerDto.class)))
    @APIResponse(responseCode = "400", description = "Ungültige Angaben", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Händler ist noch nicht angelegt", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "409", description = "Kundennummer schon vergeben", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response create(CustomerRequest request, @Context UriInfo uriInfo) {
        String dealerId = user.requireDealerId();
        validate(request, null);
        Customer customer = new Customer();
        customer.dealer = Dealer.<Dealer> findByIdOptional(dealerId)
                .orElseThrow(() -> ApiErrors.notFound("Unbekannter Händler: " + dealerId));
        request.applyTo(customer);
        customer.persist();
        return Response.created(uriInfo.getAbsolutePathBuilder().path(String.valueOf(customer.id)).build())
                .entity(CustomerDto.from(customer)).build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Kunden ändern", description = "Ersetzt alle Felder des Kunden.")
    @APIResponse(responseCode = "200", description = "Geändert", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CustomerDto.class)))
    @APIResponse(responseCode = "400", description = "Ungültige Angaben", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Unbekannter Kunde oder Kunde eines anderen Händlers", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "409", description = "Kundennummer schon vergeben", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public CustomerDto update(@Parameter(description = "ID des Kunden", example = "1") @PathParam("id") long id,
            CustomerRequest request) {
        Customer customer = find(id);
        validate(request, customer.id);
        request.applyTo(customer);
        return CustomerDto.from(customer);
    }

    private Customer find(long id) {
        return Customer.findForDealer(id, user.requireDealerId())
                .orElseThrow(() -> ApiErrors.notFound("Unbekannter Kunde: " + id));
    }

    private static void validate(CustomerRequest request, Long ownId) {
        if (request == null) {
            throw ApiErrors.badRequest("Die Angaben fehlen.");
        }
        request.validate();
        if (request.externalId() != null && !request.externalId().isBlank()) {
            Customer.findByExternalId(request.externalId().strip()).filter(other -> !Objects.equals(other.id, ownId))
                    .ifPresent(other -> {
                        throw ApiErrors.conflict("Kundennummer schon vergeben: " + request.externalId().strip());
                    });
        }
    }
}
