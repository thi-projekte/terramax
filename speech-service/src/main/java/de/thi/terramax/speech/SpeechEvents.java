package de.thi.terramax.speech;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Publiziert Events auf {@code terramax.speech.events} als CloudEvent 1.0 im structured mode (Vertrag:
 * docs/kafka/README.md). Key und {@code subject} sind die {@code visitId}, {@code userid} ist der Keycloak-{@code sub}
 * des Aufrufers. Transkripte und Protokolle stehen nie im Event.
 * <p>
 * Ein Ausfall von Kafka bricht keine Anfrage ab, er wird nur geloggt: Die Sprachnotiz ist für den Nutzer wichtiger als
 * die Benachrichtigung.
 */
@ApplicationScoped
public class SpeechEvents {

    static final String CHANNEL = "speech-events";
    static final String TYPE_PREFIX = "de.thi.terramax.speech.";
    private static final Logger LOG = Logger.getLogger(SpeechEvents.class);

    @Inject
    @Channel(CHANNEL)
    Emitter<String> emitter;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SecurityIdentity identity;

    public void transcriptionCompleted(String visitId, Transcription transcription) {
        Map<String, Object> data = data(visitId);
        data.put("language", transcription.language());
        data.put("durationSeconds", transcription.durationSeconds());
        publish("transcription.completed", visitId, data);
    }

    public void transcriptionFailed(String visitId, int status) {
        Map<String, Object> data = data(visitId);
        data.put("status", status);
        publish("transcription.failed", visitId, data);
    }

    public void protocolDrafted(String visitId) {
        publish("protocol.drafted", visitId, data(visitId));
    }

    public void protocolFailed(String visitId, int status) {
        Map<String, Object> data = data(visitId);
        data.put("status", status);
        publish("protocol.failed", visitId, data);
    }

    public void protocolSubmitted(String visitId, long tourId, long customerId, Long protocolId) {
        Map<String, Object> data = data(visitId);
        data.put("tourId", tourId);
        data.put("customerId", customerId);
        data.put("protocolId", protocolId);
        publish("protocol.submitted", visitId, data);
    }

    // --- Schnellerfassung (#134): Key und subject sind die captureId der Erfassung, nie Kontaktdaten ---

    public void businessCardRead(String captureId) {
        publish("business-card.read", captureId, captureData(captureId));
    }

    public void businessCardFailed(String captureId, int status) {
        Map<String, Object> data = captureData(captureId);
        data.put("status", status);
        publish("business-card.failed", captureId, data);
    }

    public void leadCaptured(String captureId, long customerId, boolean newCustomer) {
        Map<String, Object> data = captureData(captureId);
        data.put("customerId", customerId);
        data.put("newCustomer", newCustomer);
        publish("lead.captured", captureId, data);
    }

    private static Map<String, Object> captureData(String captureId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("captureId", captureId);
        return data;
    }

    private static Map<String, Object> data(String visitId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("visitId", visitId);
        return data;
    }

    /** Keycloak-{@code sub}; danach stellt der notifications-Service zu. */
    private String userId() {
        if (identity.getPrincipal() instanceof JsonWebToken jwt && jwt.getSubject() != null) {
            return jwt.getSubject();
        }
        return identity.getPrincipal().getName();
    }

    private void publish(String type, String visitId, Map<String, Object> data) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("specversion", "1.0");
        event.put("id", UUID.randomUUID().toString());
        event.put("type", TYPE_PREFIX + type);
        event.put("source", "/speech-service");
        event.put("time", Instant.now().toString());
        event.put("subject", visitId);
        event.put("userid", userId());
        event.put("datacontenttype", "application/json");
        event.put("data", data);

        try {
            RecordHeaders headers = new RecordHeaders();
            headers.add("content-type", "application/cloudevents+json".getBytes(StandardCharsets.UTF_8));
            OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata.<String> builder()
                    .withKey(visitId)
                    .withHeaders(headers)
                    .build();
            Message<String> message = Message.of(objectMapper.writeValueAsString(event))
                    .addMetadata(metadata)
                    .withAck(() -> CompletableFuture.completedFuture(null))
                    .withNack(e -> {
                        LOG.warnf("Event %s für Besuch %s nicht zugestellt: %s", type, visitId, e.getMessage());
                        return CompletableFuture.completedFuture(null);
                    });
            emitter.send(message);
        } catch (JsonProcessingException | RuntimeException e) {
            // z. B. Puffer voll, weil Kafka länger nicht erreichbar ist
            LOG.warnf("Event %s für Besuch %s nicht gesendet: %s", type, visitId, e.getMessage());
        }
    }
}
