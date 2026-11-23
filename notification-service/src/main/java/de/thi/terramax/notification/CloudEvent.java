package de.thi.terramax.notification;

import java.time.Instant;
import java.util.Map;

/**
 * Die Felder des CloudEvent-Umschlags aus Kafka, die der Service braucht (docs/kafka/README.md).
 */
public record CloudEvent(String id, String type, Instant time, String subject, String userid,
        Map<String, Object> data) {
}
