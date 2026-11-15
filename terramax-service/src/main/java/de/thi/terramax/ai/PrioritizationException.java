package de.thi.terramax.ai;

/**
 * Die Priorisierung ist fehlgeschlagen. Die Meldung enthält nie Kundendaten oder Fehlertexte des LLM-Anbieters.
 */
public class PrioritizationException extends RuntimeException {

    public PrioritizationException(String message) {
        super(message);
    }
}
