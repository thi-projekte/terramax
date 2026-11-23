package de.thi.terramax.speech.terramax;

import de.thi.terramax.speech.terramax.TerraMaxApi.CreateProtocolRequest;
import de.thi.terramax.speech.terramax.TerraMaxApi.SavedProtocol;
import de.thi.terramax.speech.terramax.TerraMaxApi.TerraMaxError;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Übergibt bestätigte Besuchsprotokolle an den terramax-service und übersetzt dessen Fehler in Antworten für die SPA.
 */
@ApplicationScoped
public class TerraMaxClient {

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
        try {
            return api.createProtocol(tourId, request, authorization);
        } catch (WebApplicationException e) {
            throw translate(e.getResponse(), e);
        } catch (ProcessingException e) {
            throw new TerraMaxException(502, "TerraMax ist gerade nicht erreichbar. Bitte erneut versuchen.",
                    "terramax-service nicht erreichbar", e);
        }
    }

    static TerraMaxException translate(Response response, Throwable cause) {
        int status = response.getStatus();
        String log = "terramax-service antwortet mit Status " + status;
        return switch (status) {
            // Inhaltliche Fehler (z. B. "Kunde nicht auf der Tour: 5") formuliert der terramax-service selbst
            case 400 -> new TerraMaxException(400, errorText(response, "Das Protokoll ist unvollständig."), log, cause);
            // Token abgelaufen oder ungültig: Die SPA meldet den Nutzer neu an
            case 401 -> new TerraMaxException(401, "Die Anmeldung ist abgelaufen. Bitte neu anmelden.", log, cause);
            case 403 -> new TerraMaxException(403, "Keine Berechtigung, Protokolle zu dieser Tour zu speichern.", log,
                    cause);
            case 404 -> new TerraMaxException(404, "Die Tour gibt es nicht oder sie ist nicht sichtbar.", log, cause);
            default ->
                    new TerraMaxException(502, "TerraMax konnte das Protokoll nicht speichern. Bitte erneut versuchen.",
                            log, cause);
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
