package de.thi.terramax.speech;

/**
 * Das Protokoll konnte nicht erstellt werden. Der Grund bestimmt die Antwort an die Oberfläche.
 */
public class ProtocolGenerationException extends RuntimeException {

    public enum Reason {
        /** Kein oder ungültiger API-Key */
        NOT_CONFIGURED,
        /** Kontingent oder Anfragelimit beim Anbieter erreicht */
        RATE_LIMITED,
        /** Anbieter nicht erreichbar, Zeitüberschreitung oder Serverfehler */
        UNAVAILABLE,
        /** Antwort passt nicht zum Protokoll-Schema */
        INVALID_RESPONSE
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
