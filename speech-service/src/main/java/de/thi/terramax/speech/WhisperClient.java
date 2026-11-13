package de.thi.terramax.speech;

import de.thi.terramax.speech.SpeechToTextException.Reason;
import de.thi.terramax.speech.WhisperApi.WhisperTranscription;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.client.api.ClientMultipartForm;

import java.nio.file.Path;

/**
 * Spracherkennung über Speaches (Whisper) mit der OpenAI-kompatiblen Schnittstelle. Ein anderer OpenAI-kompatibler
 * Dienst lässt sich allein über die Konfiguration anbinden.
 */
@ApplicationScoped
public class WhisperClient implements SpeechToTextClient {

    @Inject
    @RestClient
    WhisperApi api;

    @Inject
    WhisperConfig config;

    @Override
    public Transcription transcribe(Path audio, String fileName, String mediaType) {
        ClientMultipartForm form = ClientMultipartForm.create()
                .binaryFileUpload("file", fileName, audio.toString(), mediaType)
                .attribute("model", config.model(), null)
                .attribute("language", config.language(), null)
                .attribute("response_format", "verbose_json", null);
        config.prompt().ifPresent(prompt -> form.attribute("prompt", prompt, null));

        try {
            WhisperTranscription result = api.transcribe(form);
            return new Transcription(result.text().strip(), result.language(), result.duration());
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            if (status == 404) {
                throw new SpeechToTextException(Reason.MODEL_NOT_READY,
                        "Whisper-Modell " + config.model() + " ist nicht installiert", e);
            }
            if (status == 400 || status == 415) {
                throw new SpeechToTextException(Reason.UNSUPPORTED_AUDIO,
                        "Whisper kann die Audiodatei nicht lesen", e);
            }
            throw new SpeechToTextException(Reason.UNAVAILABLE, "Whisper antwortet mit Status " + status, e);
        } catch (ProcessingException e) {
            throw new SpeechToTextException(Reason.UNAVAILABLE, "Whisper ist nicht erreichbar", e);
        }
    }
}
