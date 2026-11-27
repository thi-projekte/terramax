package de.thi.terramax.speech.terramax;

import com.fasterxml.jackson.databind.JsonNode;
import de.thi.terramax.speech.terramax.TerraMaxApi.CreateLeadRequest;
import de.thi.terramax.speech.terramax.TerraMaxApi.CreateProtocolRequest;
import de.thi.terramax.speech.terramax.TerraMaxApi.SavedProtocol;
import de.thi.terramax.speech.terramax.TerraMaxApi.TerraMaxError;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.function.Supplier;

/**
 * Übergibt bestätigte Besuchsprotokolle und Leads an den terramax-service und übersetzt dessen Fehler in Antworten für
 * die SPA.
 */
@ApplicationScoped
public class TerraMaxClient {

    /** Meldungen für die Oberfläche, je nachdem, was übergeben wird. */
    record Messages(String badRequest, String forbidden, String notFound, String failed) {
    }

    static final Messages PROTOCOL = new Messages("Das Protokoll ist unvollständig.",
            "Keine Berechtigung, Protokolle zu dieser Tour zu speichern.",
            "Die Tour gibt es nicht oder sie ist nicht sichtbar.",
            "TerraMax konnte das Protokoll nicht speichern. Bitte erneut versuchen.");

    static final Messages LEAD = new Messages("Die Angaben zum Lead sind unvollständig.",
            "Keine Berechtigung, Leads zu speichern.", "Der Kunde oder Händler ist in TerraMax nicht bekannt.",
            "TerraMax konnte den Lead nicht speichern. Bitte erneut versuchen.");

    @Inject
    @RestClient
    TerraMaxApi api;

    /**
     * @param authorization
     *            Authorization-Header der SPA ({@code Bearer <token>}), wird unverändert weitergereicht
     *
     * @throws TerraMaxException
     *             wenn der terramax-service ablehnt oder nicht erreichbar ist
     */
    public SavedProtocol createProtocol(long tourId, CreateProtocolRequest request, String authorization) {
        return call(() -> api.createProtocol(tourId, request, authorization), PROTOCOL);
    }

    /**
     * @param authorization
     *            Authorization-Header der SPA ({@code Bearer <token>}), wird unverändert weitergereicht
     *
     * @return Antwort des terramax-service (LeadDto)
     *
     * @throws TerraMaxException
     *             wenn der terramax-service ablehnt oder nicht erreichbar ist
     */
    public JsonNode createLead(CreateLeadRequest request, String authorization) {
        return call(() -> api.createLead(request, authorization), LEAD);
    }

    private static <T> T call(Supplier<T> call, Messages messages) {
        try {
            return call.get();
        } catch (WebApplicationException e) {
            throw translate(e.getResponse(), e, messages);
        } catch (ProcessingException e) {
            throw new TerraMaxException(502, "TerraMax ist gerade nicht erreichbar. Bitte erneut versuchen.",
                    "terramax-service nicht erreichbar", e);
        }
    }

    static TerraMaxException translate(Response response, Throwable cause, Messages messages) {
        int status = response.getStatus();
        String log = "terramax-service antwortet mit Status " + status;
        return switch (status) {
            // Inhaltliche Fehler (z. B. "Kunde nicht auf der Tour: 5") formuliert der terramax-service selbst
            case 400 -> new TerraMaxException(400, errorText(response, messages.badRequest()), log, cause);
            // Token abgelaufen oder ungültig: Die SPA meldet den Nutzer neu an
            case 401 -> new TerraMaxException(401, "Die Anmeldung ist abgelaufen. Bitte neu anmelden.", log, cause);
            case 403 -> new TerraMaxException(403, messages.forbidden(), log, cause);
            case 404 -> new TerraMaxException(404, messages.notFound(), log, cause);
            default -> new TerraMaxException(502, messages.failed(), log, cause);
        };
    }

    private static String errorText(Response response, String fallback) {
        try {
            TerraMaxError error = response.readEntity(TerraMaxError.class);
            return error != null && error.error() != null && !error.error().isBlank() ? error.error() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
