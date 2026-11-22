package de.thi.terramax.protocol;

import de.thi.terramax.common.ApiErrors;
import de.thi.terramax.common.ErrorResponse;
import de.thi.terramax.common.TerraMaxApplication;
import de.thi.terramax.security.CurrentUser;
import de.thi.terramax.security.Roles;
import de.thi.terramax.tour.TourAccess;
import de.thi.terramax.tour.TourCustomer;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * Ein Besuchsprotokoll lesen, ändern und löschen. Lesen darf, wer die Tour sieht ({@link TourAccess}); ändern und
 * löschen nur der Ersteller ({@code createdBy} = Claim {@code sub}) oder das Service-Konto ({@code process_engine}).
 * Angelegt wird ein Protokoll über {@code POST /tours/{tourId}/protocols} (Format des speech-service).
 */
@Path("/protocols/{id}")
@RolesAllowed({ Roles.SALES_PROFESSIONAL, Roles.DEALER_PRINCIPAL, Roles.PROCESS_ENGINE })
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Besuchsprotokolle")
@SecurityRequirement(name = TerraMaxApplication.BEARER_SCHEME)
@APIResponse(responseCode = "401", description = "Kein oder ungültiger Token")
@APIResponse(responseCode = "403", description = "Keine passende Rolle, kein dealer_id im Token oder (beim Ändern und Löschen) weder Ersteller noch Service-Konto", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
@APIResponse(responseCode = "404", description = "Unbekanntes oder nicht sichtbares Protokoll", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
public class ProtocolResource {

    private static final Logger LOG = Logger.getLogger(ProtocolResource.class);

    static final String EXAMPLE_PROTOCOL = """
            {"id": 7, "tourId": 1, "customerId": 3, "contactPerson": "Herr Huber (Betriebsleiter)",
             "topics": ["Neuer Traktor", "Finanzierung"], "outcome": "Interesse an einem 6R 250",
             "nextSteps": ["Angebot schicken"], "followUpDate": "2026-12-01", "transcript": "War heute bei Herrn Huber ...",
             "createdBy": "4f1c2d3e-5a6b-7c8d-9e0f-112233445566", "createdAt": "2026-11-24T09:54:10.123+01:00"}""";

    @Inject
    CurrentUser user;

    @Inject
    TourAccess access;

    @GET
    @Operation(summary = "Protokoll abrufen")
    @APIResponse(responseCode = "200", description = "Protokoll", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ProtocolDto.class), examples = @ExampleObject(name = "Protokoll", value = EXAMPLE_PROTOCOL)))
    public ProtocolDto get(@Parameter(description = "ID des Protokolls", example = "7") @PathParam("id") long id) {
        return ProtocolDto.from(visibleProtocol(id));
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Protokoll ersetzen", description = "Setzt alle Inhaltsfelder neu, fehlende werden leer. "
            + "customerId ist optional; wenn gesetzt, muss der Kunde auf derselben Tour sein. Tour, Ersteller und "
            + "Zeitpunkt bleiben.")
    @RequestBody(required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CreateProtocolRequest.class), examples = @ExampleObject(name = "Ersetzen", value = """
            {"contactPerson": "Herr Huber (Betriebsleiter)", "topics": ["Neuer Traktor"],
             "outcome": "Angebot gewünscht", "nextSteps": ["Angebot bis Freitag schicken"], "followUpDate": "2026-11-28"}""")))
    @APIResponse(responseCode = "200", description = "Geändert", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ProtocolDto.class)))
    @APIResponse(responseCode = "400", description = "Ungültige Angaben", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public ProtocolDto replace(@Parameter(description = "ID des Protokolls", example = "7") @PathParam("id") long id,
            CreateProtocolRequest request) {
        VisitorProtocol protocol = editableProtocol(id, request);
        moveToCustomer(protocol, request.customerId());
        request.applyTo(protocol);
        LOG.infof("Protokoll %d ersetzt", protocol.id);
        return ProtocolDto.from(protocol);
    }

    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Protokoll teilweise ändern", description = "Nur gesetzte Felder werden übernommen; ein "
            + "leerer Text oder eine leere Liste löscht den Wert.")
    @RequestBody(required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CreateProtocolRequest.class), examples = @ExampleObject(name = "Ergebnis ändern", value = "{\"outcome\": \"Probefahrt vereinbart\", \"followUpDate\": \"2026-12-05\"}")))
    @APIResponse(responseCode = "200", description = "Geändert", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ProtocolDto.class)))
    @APIResponse(responseCode = "400", description = "Ungültige Angaben", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public ProtocolDto patch(@Parameter(description = "ID des Protokolls", example = "7") @PathParam("id") long id,
            CreateProtocolRequest request) {
        VisitorProtocol protocol = editableProtocol(id, request);
        moveToCustomer(protocol, request.customerId());
        request.mergeInto(protocol);
        LOG.infof("Protokoll %d geändert", protocol.id);
        return ProtocolDto.from(protocol);
    }

    @DELETE
    @Transactional
    @Operation(summary = "Protokoll löschen", description = "Nur Ersteller oder Service-Konto. Ein zweiter Aufruf "
            + "antwortet mit 404.")
    @APIResponse(responseCode = "204", description = "Gelöscht")
    public void delete(@Parameter(description = "ID des Protokolls", example = "7") @PathParam("id") long id) {
        VisitorProtocol protocol = visibleProtocol(id);
        requireMayEdit(protocol);
        protocol.delete();
        LOG.infof("Protokoll %d gelöscht", id);
    }

    private VisitorProtocol visibleProtocol(long id) {
        access.requireCaller();
        return VisitorProtocol.<VisitorProtocol> findByIdOptional(id).filter(p -> access.isVisible(p.tour))
                .orElseThrow(() -> ApiErrors.notFound("Unbekanntes Protokoll: " + id));
    }

    private VisitorProtocol editableProtocol(long id, CreateProtocolRequest request) {
        VisitorProtocol protocol = visibleProtocol(id);
        requireMayEdit(protocol);
        if (request == null) {
            throw ApiErrors.badRequest("Die Angaben fehlen.");
        }
        request.validateUpdate();
        return protocol;
    }

    /** Ersteller oder Service-Konto. */
    private void requireMayEdit(VisitorProtocol protocol) {
        if (user.isServiceAccount()) {
            return;
        }
        if (!user.subject().map(protocol.createdBy::equals).orElse(false)) {
            throw ApiErrors.forbidden("Nur der Ersteller darf das Protokoll ändern oder löschen.");
        }
    }

    private static void moveToCustomer(VisitorProtocol protocol, Long customerId) {
        if (customerId == null || customerId.equals(protocol.customer.id)) {
            return;
        }
        TourCustomer stop = protocol.tour.stop(customerId)
                .orElseThrow(() -> ApiErrors.badRequest("Kunde nicht auf der Tour: " + customerId));
        protocol.customer = stop.customer;
    }
}
