package de.thi.terramax.notification;

import java.time.Instant;
import java.util.Map;

/**
 * Eine Benachrichtigung, wie die SPA sie bekommt. Die Felder stammen aus dem CloudEvent (docs/kafka/README.md).
 */
public record Notification(String id, String type, Instant time, String subject, Map<String, Object> data) {
}
