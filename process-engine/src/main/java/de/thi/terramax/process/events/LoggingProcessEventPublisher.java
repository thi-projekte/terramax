package de.thi.terramax.process.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schreibt Prozess-Events nur ins Log. Standard, solange {@code KAFKA_ENABLED} nicht {@code true} ist (lokal, Tests).
 */
public class LoggingProcessEventPublisher implements ProcessEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingProcessEventPublisher.class);

    @Override
    public void publish(ProcessEvent event) {
        LOG.info("Prozess-Event (Kafka aus): {} für {}", event.type(), event.subject());
    }
}
