package de.thi.terramax.process.events;

/**
 * Gibt ein Prozess-Event weiter. Auf dem Server nach Kafka ({@link KafkaProcessEventPublisher}), lokal und in Tests nur
 * ins Log ({@link LoggingProcessEventPublisher}). Fehler beim Senden werden geloggt und nicht weitergeworfen, weil der
 * Prozessschritt zu diesem Zeitpunkt bereits gespeichert ist.
 */
public interface ProcessEventPublisher {

    void publish(ProcessEvent event);
}
