package de.thi.terramax.speech;

/**
 * Das Protokoll konnte nicht erstellt werden. Der Grund bestimmt die Antwort an die Oberfläche.
 */
public class ProtocolGenerationException extends RuntimeException {

    public enum Reason {
        /** Kein oder ungültiger API-Key */
        NOT_CONFIGURED(502),
        /** Kontingent oder Anfragelimit beim Anbieter erreicht */
        RATE_LIMITED(503),
        /** Anbieter nicht erreichbar, Zeitüberschreitung oder Serverfehler */
        UNAVAILABLE(502),
        /** Antwort passt nicht zum Protokoll-Schema */
        INVALID_RESPONSE(502);

        private final int status;

        Reason(int status) {
            this.status = status;
        }

        /** HTTP-Status der Antwort; steht auch im Event protocol.failed. */
        public int status() {
            return status;
        }
    }

    private final Reason reason;

    public ProtocolGenerationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
