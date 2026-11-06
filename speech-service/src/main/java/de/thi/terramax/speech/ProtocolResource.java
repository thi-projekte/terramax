package de.thi.terramax.speech;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Erstellt aus dem Transkript einer Sprachnotiz einen Protokollentwurf. Der Entwurf wird nicht gespeichert: Der Sales
 * Professional bearbeitet und bestätigt ihn in der Oberfläche, erst dann wird er dem Kunden zugeordnet. Transkript und
 * Protokoll werden nicht geloggt.
 */
@Path("/protocols")
@RolesAllowed(Roles.SALES_PROFESSIONAL)
public class ProtocolResource {

    private static final Logger LOG = Logger.getLogger(ProtocolResource.class);
    // Bezugsdatum für "nächste Woche" usw.: Die Nutzer sitzen in Deutschland
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    // Eine Notiz von 2 Minuten hat etwa 2 000 Zeichen
    static final int MAX_TRANSCRIPT_LENGTH = 5_000;

    @Inject
    ProtocolGenerator generator;

    public record ProtocolRequest(String visitId, String transcript) {
    }

    public record ProtocolResponse(String visitId, VisitProtocol protocol) {
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ProtocolResponse create(ProtocolRequest request) {
        if (request == null || request.visitId() == null || request.visitId().isBlank()) {
            throw ErrorResponse.exception(Status.BAD_REQUEST, "Die visitId fehlt.");
        }
        if (request.transcript() == null || request.transcript().isBlank()) {
            throw ErrorResponse.exception(Status.BAD_REQUEST, "Das Transkript ist leer.");
        }
        if (request.transcript().length() > MAX_TRANSCRIPT_LENGTH) {
            throw ErrorResponse.exception(Status.BAD_REQUEST, "Das Transkript ist zu lang.");
        }

        VisitProtocol protocol = generator.generate(request.transcript().strip(), LocalDate.now(ZONE));
        LOG.infof("Protokollentwurf für Besuch %s erstellt", request.visitId());
        return new ProtocolResponse(request.visitId(), protocol);
    }

    @ServerExceptionMapper
    public Response mapProtocolError(ProtocolGenerationException e) {
        LOG.warnf("Protokoll konnte nicht erstellt werden (%s): %s", e.reason(), e.getMessage());
        return switch (e.reason()) {
            case RATE_LIMITED -> ErrorResponse.of(Status.SERVICE_UNAVAILABLE,
                    "Die AI ist gerade ausgelastet. Bitte gleich erneut versuchen.");
            case NOT_CONFIGURED, UNAVAILABLE -> ErrorResponse.of(Status.BAD_GATEWAY,
                    "Die AI ist gerade nicht erreichbar. Das Protokoll kann auch von Hand ausgefüllt werden.");
            case INVALID_RESPONSE -> ErrorResponse.of(Status.BAD_GATEWAY,
                    "Die AI hat kein gültiges Protokoll geliefert. Bitte erneut versuchen.");
        };
    }
}
