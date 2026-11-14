package de.thi.terramax.speech;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.client.api.ClientMultipartForm;

/**
 * OpenAI-kompatible Transkriptions-Schnittstelle von Speaches (Whisper).
 */
@RegisterRestClient(configKey = "whisper")
public interface WhisperApi {

    @POST
    @Path("/v1/audio/transcriptions")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    WhisperTranscription transcribe(ClientMultipartForm form);

    /** Antwort bei response_format=verbose_json; weitere Felder wie segments werden ignoriert. */
    record WhisperTranscription(String text, String language, double duration) {
    }
}
