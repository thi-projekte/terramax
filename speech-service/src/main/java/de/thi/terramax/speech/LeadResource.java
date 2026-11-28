package de.thi.terramax.speech;

import com.fasterxml.jackson.databind.JsonNode;
import de.thi.terramax.speech.terramax.TerraMaxApi.CreateLeadRequest;
import de.thi.terramax.speech.terramax.TerraMaxApi.LeadContact;
import de.thi.terramax.speech.terramax.TerraMaxClient;
import de.thi.terramax.speech.terramax.TerraMaxException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Übergibt einen vom Sales Professional bestätigten Lead an TerraMax ({@code POST /leads} im terramax-service). Ablauf
 * in der SPA: {@code POST /business-cards} (KI-Entwurf) → optional {@code POST /transcriptions} (Kommentar diktiert) →
 * Nutzer prüft und bearbeitet → dieser Endpunkt.
 * <p>
 * Wie bei {@link TourProtocolResource} reicht der speech-service den Authorization-Header der SPA unverändert weiter;
 * TerraMax speichert den Lead mit dem Nutzer als Ersteller. Kontaktdaten und Kommentar werden nicht geloggt.
 */
@Path("/leads")
@RolesAllowed(Roles.SALES_PROFESSIONAL)
public class LeadResource {

    private static final Logger LOG = Logger.getLogger(LeadResource.class);
    /** Grenzen des terramax-service (CreateLeadRequest). */
    static final int MAX_COMMENT_LENGTH = 2_000;
    static final int MAX_TRANSCRIPT_LENGTH = 20_000;

    @Inject
    TerraMaxClient terraMax;

    @Inject
    SpeechEvents events;

    /**
     * Bestätigter Lead: Felder wie {@code CreateLeadRequest} im terramax-service, dazu die {@code captureId} der
     * Erfassung (dieselbe wie bei {@code /business-cards} und {@code /transcriptions}).
     */
    public record SubmitLeadRequest(String captureId, String source, String eventName, LeadContact contact,
            Double latitude, Double longitude, String comment, String transcript, Long existingCustomerId) {
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response submit(SubmitLeadRequest request, @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        if (request == null || request.captureId() == null || request.captureId().isBlank()) {
            throw ErrorResponse.exception(Status.BAD_REQUEST, "Die captureId fehlt.");
        }
        if (request.contact() == null) {
            throw ErrorResponse.exception(Status.BAD_REQUEST, "Name oder Firma fehlt.");
        }
        if (request.comment() != null && request.comment().length() > MAX_COMMENT_LENGTH) {
            throw ErrorResponse.exception(Status.BAD_REQUEST, "Der Kommentar ist zu lang.");
        }
        if (request.transcript() != null && request.transcript().length() > MAX_TRANSCRIPT_LENGTH) {
            throw ErrorResponse.exception(Status.BAD_REQUEST, "Das Transkript ist zu lang.");
        }

        JsonNode saved = terraMax.createLead(new CreateLeadRequest(request.source(), request.eventName(),
                request.contact(), request.latitude(), request.longitude(), request.comment(), request.transcript(),
                request.existingCustomerId()), authorization);

        long customerId = saved.path("customerId").asLong();
        boolean newCustomer = saved.path("newCustomer").asBoolean();
        events.leadCaptured(request.captureId(), customerId, newCustomer);
        LOG.infof("Lead der Erfassung %s an TerraMax übergeben (Kunde %d)", request.captureId(), customerId);
        return Response.status(Status.CREATED).entity(saved).build();
    }

    @ServerExceptionMapper
    public Response mapTerraMaxError(TerraMaxException e) {
        LOG.warnf("Lead nicht an TerraMax übergeben: %s", e.getMessage());
        return ErrorResponse.of(Status.fromStatusCode(e.status()), e.userMessage());
    }
}
