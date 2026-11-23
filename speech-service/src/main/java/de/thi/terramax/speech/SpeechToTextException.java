package de.thi.terramax.speech;

/**
 * Fehler bei der Spracherkennung. Der Grund bestimmt den HTTP-Status der Antwort.
 */
public class SpeechToTextException extends RuntimeException {

    public enum Reason {
        /** Die Datei ist kein lesbares Audio. */
        UNSUPPORTED_AUDIO(415, "Die Audiodatei konnte nicht gelesen werden."),
        /** Anfragelimit bzw. Kontingent beim Anbieter erreicht. */
        RATE_LIMITED(503, "Die Spracherkennung ist gerade ausgelastet. Bitte gleich erneut versuchen."),
        /** Der Dienst ist nicht erreichbar oder antwortet mit einem Fehler. */
        UNAVAILABLE(502, "Die Spracherkennung ist gerade nicht erreichbar.");

        private final int status;
        private final String userMessage;

        Reason(int status, String userMessage) {
            this.status = status;
            this.userMessage = userMessage;
        }

        /** HTTP-Status der Antwort; steht auch im Event transcription.failed. */
        public int status() {
            return status;
        }

        /** Meldung für die Oberfläche. */
        public String userMessage() {
            return userMessage;
        }
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
