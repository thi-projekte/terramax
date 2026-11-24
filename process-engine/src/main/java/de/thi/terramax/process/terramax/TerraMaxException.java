package de.thi.terramax.process.terramax;

/**
 * Fehler beim Aufruf des terramax-service. {@link #status()} ist der HTTP-Status, 0 wenn keine Antwort kam.
 */
public abstract sealed class TerraMaxException extends RuntimeException
        permits TerraMaxFachlicherFehler, TerraMaxTechnischerFehler {

    private final int status;

    protected TerraMaxException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
