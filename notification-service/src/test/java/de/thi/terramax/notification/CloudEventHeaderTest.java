package de.thi.terramax.notification;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Schickt ein Event wie der speech-service über Kafka: structured mode mit Header content-type
 * application/cloudevents+json. Ohne cloud-events=false übergibt der Connector nur data und der Consumer bricht ab.
 */
@QuarkusTest
class CloudEventHeaderTest {

    private static final String EVENT = """
            {
              "specversion": "1.0",
              "id": "e-header",
              "type": "de.thi.terramax.speech.protocol.drafted",
              "source": "/speech-service",
              "time": "2026-11-20T13:50:00Z",
              "subject": "visit-1",
              "userid": "user-erik",
              "datacontenttype": "application/json",
              "data": { "visitId": "visit-1" }
            }
            """;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Inject
    NotificationStore store;

    @Test
    void structuredCloudEventFromSpeechServiceIsStored() throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>("terramax.speech.events", "visit-1", EVENT);
        record.headers().add("content-type", "application/cloudevents+json".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(config)) {
            producer.send(record).get();
        }

        // Der Consumer liest asynchron, deshalb bis zu 30 Sekunden warten
        for (int i = 0; i < 300 && store.forUser("user-erik").isEmpty(); i++) {
            Thread.sleep(100);
        }
        List<Notification> list = store.forUser("user-erik");
        assertEquals(1, list.size());
        assertEquals("e-header", list.get(0).id());
    }
}
