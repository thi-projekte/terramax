package de.thi.terramax.engine;

/**
 * Anbindung an die process-engine (CIB Seven). Implementierungen: {@link StubProcessEngineClient} (Standard, ruft
 * nichts auf) und {@link RestProcessEngineClient} ({@code /engine-rest}). Auswahl über
 * {@code terramax.engine.mode=stub|rest}, siehe {@link ProcessEngineClientProducer}.
 * <p>
 * TODO(#64-meeting): Aufrufrichtung und Prozess-Key mit process-engine klären; CIB Seven auf dem Server kommt mit #63
 */
public interface ProcessEngineClient {

    /**
     * Startet den Besuchstour-Prozess für eine Tour.
     *
     * @return ID der Prozessinstanz (für {@code tour.process_instance_id})
     *
     * @throws ProcessEngineException
     *             wenn die Engine nicht erreichbar ist oder ablehnt
     */
    String startTour(long tourId);

    /**
     * @throws ProcessEngineException
     *             wenn die Engine nicht erreichbar ist oder ablehnt
     */
    ProcessStatus getStatus(String processInstanceId);

    /** Zustand einer Prozessinstanz. {@code ENDED}: Die Engine kennt sie nicht mehr als laufende Instanz. */
    enum ProcessStatus {
        ACTIVE, SUSPENDED, ENDED
    }
}
