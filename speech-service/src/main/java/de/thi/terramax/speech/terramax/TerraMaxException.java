package de.thi.terramax.speech.terramax;

/**
 * Der terramax-service hat das Protokoll nicht angenommen oder ist nicht erreichbar.
 */
public class TerraMaxException extends RuntimeException {

    private final int status;
    private final String userMessage;

    /**
     * @param status
     *            HTTP-Status für die Antwort an die SPA
     * @param userMessage
     *            Meldung für die Oberfläche
     */
    public TerraMaxException(int status, String userMessage, String logMessage, Throwable cause) {
        super(logMessage, cause);
        this.status = status;
        this.userMessage = userMessage;
    }

    public int status() {
        return status;
    }

    public String userMessage() {
        return userMessage;
    }
}
