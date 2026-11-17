package de.thi.terramax.speech;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.client.api.ClientMultipartForm;

import java.util.List;

/**
 * Transkriptions-Schnittstelle von OpenAI ({@code POST /v1/audio/transcriptions}). Der API-Key ist derselbe wie für die
 * Protokolle (quarkus.langchain4j.openai.api-key, also OPENAI_API_KEY).
 */
@RegisterRestClient(configKey = "openai-transcription")
@ClientHeaderParam(name = HttpHeaders.AUTHORIZATION, value = "{authorization}")
public interface OpenAiTranscriptionApi {

    @POST
    @Path("/audio/transcriptions")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    OpenAiTranscription transcribe(ClientMultipartForm form);

    default String authorization() {
        return "Bearer " + ConfigProvider.getConfig().getValue("quarkus.langchain4j.openai.api-key", String.class);
    }

    /**
     * Antwort bei response_format=json. {@code languages} liefert gpt-transcribe; {@code usage} ist je nach Modell nach
     * Tokens oder nach Dauer abgerechnet. Weitere Felder werden ignoriert.
     */
    record OpenAiTranscription(String text, List<DetectedLanguage> languages, Usage usage) {
    }

    record DetectedLanguage(String code) {
    }

    /** {@code seconds} gibt es nur bei Abrechnung nach Dauer (type = duration). */
    record Usage(String type, Double seconds) {
    }
}
