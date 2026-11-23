package de.thi.terramax.speech;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.file.Path;

/**
 * Transkribiert eine Sprachnotiz und meldet das Ergebnis als Event. Gemeinsam für REST ({@link TranscriptionResource})
 * und WebSocket ({@link TranscriptionSocket}). Das Transkript wird nicht geloggt.
 */
@ApplicationScoped
public class Transcriber {

    private static final Logger LOG = Logger.getLogger(Transcriber.class);

    @Inject
    SpeechToTextClient speechToText;

    @Inject
    SpeechEvents events;

    /**
     * @throws SpeechToTextException
     *             wenn die Erkennung nicht möglich ist
     */
    public Transcription transcribe(String visitId, Path audio, String fileName, String mediaType) {
        Transcription transcription;
        try {
            transcription = speechToText.transcribe(audio, fileName, mediaType);
        } catch (SpeechToTextException e) {
            events.transcriptionFailed(visitId, e.reason().status());
            throw e;
        }
        events.transcriptionCompleted(visitId, transcription);
        LOG.infof("Sprachnotiz für Besuch %s transkribiert", visitId);
        return transcription;
    }

    /** Medientyp ohne Parameter, z. B. "audio/webm" für "audio/webm;codecs=opus". */
    static String mediaType(String contentType) {
        return contentType == null ? "" : contentType.split(";")[0].strip();
    }
}
