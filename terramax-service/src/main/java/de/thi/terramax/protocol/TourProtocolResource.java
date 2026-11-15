package de.thi.terramax.protocol;

import de.thi.terramax.common.ApiErrors;
import de.thi.terramax.common.ErrorResponse;
import de.thi.terramax.common.TerraMaxApplication;
import de.thi.terramax.security.CurrentUser;
import de.thi.terramax.security.Roles;
import de.thi.terramax.tour.Tour;
import de.thi.terramax.tour.TourAccess;
import de.thi.terramax.tour.TourCustomer;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * Besuchsprotokolle einer Tour. Wer die Tour sieht ({@link TourAccess}), sieht auch ihre Protokolle. Inhalte von
 * Protokollen und Transkripten werden nicht geloggt.
 */
@Path("/tours/{tourId}/protocols")
@RolesAllowed({ Roles.SALES_PROFESSIONAL, Roles.DEALER_PRINCIPAL })
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Besuchsprotokolle")
@SecurityRequirement(name = TerraMaxApplication.BEARER_SCHEME)
@APIResponse(responseCode = "401", description = "Kein oder ungültiger Token")
@APIResponse(responseCode = "403", description = "Keine TerraMax-Rolle oder kein dealer_id im Token", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
@APIResponse(responseCode = "404", description = "Unbekannte oder nicht sichtbare Tour", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
public class TourProtocolResource {

    private static final Logger LOG = Logger.getLogger(TourProtocolResource.class);

    @Inject
    CurrentUser user;

    @Inject
    TourAccess access;

    @GET
    @Operation(summary = "Protokolle einer Tour", description = "Neueste zuerst.")
    @APIResponse(responseCode = "200", description = "Protokolle", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ProtocolDto.class, type = SchemaType.ARRAY)))
    public List<ProtocolDto> list(
            @Parameter(description = "ID der Tour", example = "1") @PathParam("tourId") long tourId) {
        Tour tour = access.visibleTour(tourId);
        return VisitorProtocol.listByTour(tour.id).stream().map(ProtocolDto::from).toList();
    }

    // TODO(#64-meeting): Aufruf durch speech-service und Token-Weitergabe klären
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Protokoll speichern", description = "Speichert ein Besuchsprotokoll zu einem Kunden der Tour. "
            + "Format wie VisitProtocol im speech-service, dazu customerId und optional transcript.")
    @APIResponse(responseCode = "201", description = "Gespeichert", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ProtocolDto.class)))
    @APIResponse(responseCode = "400", description = "Ungültige Angaben, z. B. Kunde nicht auf der Tour", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response create(@Parameter(description = "ID der Tour", example = "1") @PathParam("tourId") long tourId,
            CreateProtocolRequest request) {
        user.requireDealerId();
        if (request == null) {
            throw ApiErrors.badRequest("Die Angaben fehlen.");
        }
        request.validate();
        String createdBy = user.requireSubject();
        Tour tour = access.visibleTour(tourId);
        TourCustomer stop = tour.stops.stream().filter(s -> s.customer.id.equals(request.customerId())).findFirst()
                .orElseThrow(() -> ApiErrors.badRequest("Kunde nicht auf der Tour: " + request.customerId()));

        VisitorProtocol protocol = new VisitorProtocol();
        protocol.tour = tour;
        protocol.customer = stop.customer;
        protocol.createdBy = createdBy;
        request.applyTo(protocol);
        protocol.persist();
        LOG.infof("Protokoll %d für Tour %d gespeichert", protocol.id, tour.id);
        return Response.status(Response.Status.CREATED).entity(ProtocolDto.from(protocol)).build();
    }
}
