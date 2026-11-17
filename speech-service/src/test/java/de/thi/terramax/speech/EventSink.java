package de.thi.terramax.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;

import java.util.List;

/**
 * Liest in den Tests die Events, die statt an Kafka an den In-Memory-Connector gehen.
 */
@ApplicationScoped
public class EventSink {

    @Inject
    @Connector("smallrye-in-memory")
    InMemoryConnector connector;

    @Inject
    ObjectMapper objectMapper;

    private InMemorySink<String> sink() {
        return connector.sink(SpeechEvents.CHANNEL);
    }

    void clear() {
        sink().clear();
    }

    /** Typen ohne Präfix, z. B. "transcription.completed". */
    List<String> types() {
        return events().stream().map(e -> e.get("type").asText().substring(SpeechEvents.TYPE_PREFIX.length()))
                .toList();
    }

    List<JsonNode> events() {
        return sink().received().stream().map(this::json).toList();
    }

    List<Object> keys() {
        return sink().received().stream()
                .map(m -> m.getMetadata(OutgoingKafkaRecordMetadata.class).orElseThrow().getKey())
                .map(Object.class::cast)
                .toList();
    }

    private JsonNode json(Message<String> message) {
        try {
            return objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
