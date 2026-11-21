package de.thi.terramax.speech;

import de.thi.terramax.speech.OpenAiTranscriptionApi.OpenAiTranscription;
import de.thi.terramax.speech.SpeechToTextException.Reason;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.client.api.ClientMultipartForm;

import java.nio.file.Path;

/**
 * Spracherkennung über die OpenAI-API (Modell aus transcription.model, Standard gpt-transcribe). Das Audio geht dafür
 * an OpenAI; gespeichert wird es im Service nicht.
 */
@ApplicationScoped
public class OpenAiTranscriptionClient implements SpeechToTextClient {

    @Inject
    @RestClient
    OpenAiTranscriptionApi api;

    @Inject
    TranscriptionConfig config;

    @Override
    public Transcription transcribe(Path audio, String fileName, String mediaType) {
        ClientMultipartForm form = ClientMultipartForm.create()
                .binaryFileUpload("file", fileName, audio.toString(), mediaType)
                .attribute("model", config.model(), null)
                .attribute("response_format", "json", null);
        // Listen schickt die OpenAI-API im Multipart-Format als wiederholte Felder mit []
        config.languages().forEach(language -> form.attribute("languages[]", language, null));
        config.keywords().ifPresent(keywords -> keywords.forEach(k -> form.attribute("keywords[]", k, null)));
        config.prompt().ifPresent(prompt -> form.attribute("prompt", prompt, null));

        OpenAiTranscription result;
        try {
            result = api.transcribe(form);
        } catch (WebApplicationException e) {
            throw translate(e.getResponse().getStatus(), e);
        } catch (ProcessingException e) {
            throw new SpeechToTextException(Reason.UNAVAILABLE, "OpenAI nicht erreichbar", e);
        }
        if (result == null || result.text() == null) {
            throw new SpeechToTextException(Reason.UNAVAILABLE, "Leere Antwort von OpenAI", null);
        }
        return new Transcription(result.text().strip(), language(result), duration(result));
    }

    // Die Antwort von OpenAI wird bewusst nicht weitergegeben, sie kann Teile der Aufnahme enthalten
    static SpeechToTextException translate(int status, Throwable cause) {
        return switch (status) {
            case 400 -> new SpeechToTextException(Reason.UNSUPPORTED_AUDIO, "OpenAI kann die Audiodatei nicht lesen",
                    cause);
            case 401, 403 -> new SpeechToTextException(Reason.UNAVAILABLE,
                    "OpenAI lehnt den API-Key ab (OPENAI_API_KEY fehlt oder ist ungültig)", cause);
            case 429 -> new SpeechToTextException(Reason.RATE_LIMITED, "Anfragelimit bei OpenAI erreicht", cause);
            default -> new SpeechToTextException(Reason.UNAVAILABLE, "OpenAI antwortet mit Status " + status, cause);
        };
    }

    /** Erkannte Sprache; ohne sichere Erkennung die erste konfigurierte. */
    private String language(OpenAiTranscription result) {
        if (result.languages() != null && !result.languages().isEmpty() && result.languages().getFirst() != null) {
            return result.languages().getFirst().code();
        }
        return config.languages().isEmpty() ? null : config.languages().getFirst();
    }

    /** Dauer gibt es nur, wenn OpenAI nach Sekunden abrechnet; gpt-transcribe rechnet nach Tokens ab. */
    private static Double duration(OpenAiTranscription result) {
        return result.usage() != null && "duration".equals(result.usage().type()) ? result.usage().seconds() : null;
    }
}
