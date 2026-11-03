package de.thi.terramax.speech;

import jakarta.ws.rs.Encoded;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Modellverwaltung von Speaches. Eigener Client, weil ein Download mehrere Minuten dauern kann
 * und deshalb einen längeren Timeout braucht als die Transkription.
 */
@RegisterRestClient(configKey = "whisper-models")
public interface WhisperModelsApi {

    /** Lädt das Modell herunter; ist es schon vorhanden, kehrt der Aufruf sofort zurück. */
    @POST
    @Path("/v1/models/{model}")
    void download(@PathParam("model") @Encoded String model); // Schrägstrich in der Modell-ID nicht kodieren
}
