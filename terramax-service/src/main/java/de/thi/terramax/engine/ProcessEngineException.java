package de.thi.terramax.engine;

/**
 * Die process-engine ist nicht erreichbar oder hat den Aufruf abgelehnt.
 */
public class ProcessEngineException extends RuntimeException {

    public ProcessEngineException(String message) {
        super(message);
    }
}
