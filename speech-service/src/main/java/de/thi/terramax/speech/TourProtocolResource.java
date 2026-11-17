package de.thi.terramax.speech;

import de.thi.terramax.speech.terramax.TerraMaxApi.CreateProtocolRequest;
import de.thi.terramax.speech.terramax.TerraMaxApi.SavedProtocol;
import de.thi.terramax.speech.terramax.TerraMaxClient;
import de.thi.terramax.speech.terramax.TerraMaxException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.time.LocalDate;
import java.util.List;

/**
 * Übergibt ein vom Sales Professional bestätigtes Besuchsprotokoll an TerraMax ({@code POST /tours/{tourId}/protocols}
 * im terramax-service). Ablauf in der SPA: {@code POST /transcriptions} → {@code POST /protocols} (KI-Entwurf) → Nutzer
 * prüft und bearbeitet → dieser Endpunkt.
 * <p>
 * Der speech-service meldet sich bei TerraMax mit dem Token der SPA an: Er reicht den Authorization-Header der Anfrage
 * unverändert weiter. TerraMax prüft das Token selbst, prüft die Sichtbarkeit der Tour und speichert das Protokoll mit
 * dem Nutzer als Ersteller. Transkript und Protokoll werden nicht geloggt.
 */
@Path("/tours/{tourId}/protocols")
@RolesAllowed(Roles.SALES_PROFESSIONAL)
public class TourProtocolResource {

    private static final Logger LOG = Logger.getLogger(TourProtocolResource.class);
    /** Grenze des terramax-service (CreateProtocolRequest.MAX_TRANSCRIPT_LENGTH). */
    static final int MAX_TRANSCRIPT_LENGTH = 20_000;

    @Inject
    TerraMaxClient terraMax;

    @Inject
    SpeechEvents events;

    /**
     * Bestätigtes Protokoll; Felder wie {@link VisitProtocol}, dazu der Kunde und optional das Transkript der
     * Sprachnotiz.
     */
    public record SubmitProtocolRequest(Long customerId, String contactPerson, List<String> topics, String outcome,
            List<String> nextSteps, LocalDate followUpDate, String transcript) {
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response submit(@PathParam("tourId") long tourId, SubmitProtocolRequest request,
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        if (request == null || request.customerId() == null) {
            throw ErrorResponse.exception(Status.BAD_REQUEST, "Die customerId fehlt.");
        }
        if (request.transcript() != null && request.transcript().length() > MAX_TRANSCRIPT_LENGTH) {
            throw ErrorResponse.exception(Status.BAD_REQUEST, "Das Transkript ist zu lang.");
        }

        SavedProtocol saved = terraMax.createProtocol(tourId, new CreateProtocolRequest(request.customerId(),
                request.contactPerson(), request.topics(), request.outcome(), request.nextSteps(),
                request.followUpDate(), request.transcript()), authorization);

        String visitId = visitId(tourId, request.customerId());
        events.protocolSubmitted(visitId, tourId, request.customerId(), saved.id());
        LOG.infof("Protokoll für Besuch %s an TerraMax übergeben (Protokoll %d)", visitId, saved.id());
        return Response.status(Status.CREATED).entity(saved).build();
    }

    /**
     * Kennung eines Besuchs für Events: Tour und Kunde. Die SPA nutzt dieselbe Form als {@code visitId} bei
     * {@code /transcriptions} und {@code /protocols}, damit alle Events eines Besuchs zusammengehören.
     */
    static String visitId(long tourId, long customerId) {
        return tourId + ":" + customerId;
    }

    @ServerExceptionMapper
    public Response mapTerraMaxError(TerraMaxException e) {
        LOG.warnf("Protokoll nicht an TerraMax übergeben: %s", e.getMessage());
        return ErrorResponse.of(Status.fromStatusCode(e.status()), e.userMessage());
    }
}
