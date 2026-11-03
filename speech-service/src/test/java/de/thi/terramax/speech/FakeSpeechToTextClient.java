package de.thi.terramax.speech;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.nio.file.Path;

/**
 * Ersetzt Whisper in den Tests. Merkt sich den letzten Aufruf und kann Fehler simulieren.
 * Zugriff nur über Methoden, weil Tests einen Proxy der Bean injiziert bekommen.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class FakeSpeechToTextClient implements SpeechToTextClient {

    private SpeechToTextException.Reason failWith;
    private String lastFileName;
    private String lastMediaType;

    void reset() {
        failWith = null;
        lastFileName = null;
        lastMediaType = null;
    }

    void failWith(SpeechToTextException.Reason reason) {
        failWith = reason;
    }

    String lastFileName() {
        return lastFileName;
    }

    String lastMediaType() {
        return lastMediaType;
    }

    @Override
    public Transcription transcribe(Path audio, String fileName, String mediaType) {
        lastFileName = fileName;
        lastMediaType = mediaType;
        if (failWith != null) {
            throw new SpeechToTextException(failWith, "simuliert", null);
        }
        return new Transcription("Besuch beim Hof Maier.", "de", 12.5);
    }
}
