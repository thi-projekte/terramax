package de.thi.terramax.speech;

/**
 * Fehler bei der Spracherkennung. Der Grund bestimmt den HTTP-Status der Antwort.
 */
public class SpeechToTextException extends RuntimeException {

    public enum Reason {
        /** Die Datei ist kein lesbares Audio. */
        UNSUPPORTED_AUDIO,
        /** Das Modell ist noch nicht geladen, z. B. direkt nach dem ersten Start. */
        MODEL_NOT_READY,
        /** Der Dienst ist nicht erreichbar oder antwortet mit einem Fehler. */
        UNAVAILABLE
    }

    private final Reason reason;

    public SpeechToTextException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
