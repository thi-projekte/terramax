package de.thi.terramax.process.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prüft das Kafka-Format aus docs/kafka/README.md: Topic, Key, Header und CloudEvent-JSON.
 */
class KafkaProcessEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KafkaProcessEventPublisher publisher = new KafkaProcessEventPublisher(kafkaTemplate,
            objectMapper, "terramax.process.events");

    @Test
    @SuppressWarnings("unchecked")
    void sendsStructuredCloudEventKeyedByProcessInstance() throws Exception {
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("processInstanceId", "pi-1");
        data.put("kundeId", "K-2");
        data.put("sprachnotizId", null);

        publisher.publish(ProcessEvent.of("visit.documented", "pi-1", "sub-123", data));

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();
        assertThat(record.topic()).isEqualTo("terramax.process.events");
        assertThat(record.key()).isEqualTo("pi-1");
        assertThat(new String(record.headers().lastHeader("content-type").value(), StandardCharsets.UTF_8))
                .isEqualTo("application/cloudevents+json");

        JsonNode json = objectMapper.readTree(record.value());
        assertThat(json.get("specversion").asText()).isEqualTo("1.0");
        assertThat(json.get("type").asText()).isEqualTo("de.thi.terramax.process.visit.documented");
        assertThat(json.get("source").asText()).isEqualTo("/process-engine");
        assertThat(json.get("subject").asText()).isEqualTo("pi-1");
        assertThat(json.get("userid").asText()).isEqualTo("sub-123");
        assertThat(json.get("datacontenttype").asText()).isEqualTo("application/json");
        assertThat(json.get("time").asText()).endsWith("Z");
        assertThat(json.get("id").asText()).isNotBlank();
        assertThat(json.get("data").get("kundeId").asText()).isEqualTo("K-2");
        assertThat(json.get("data").has("sprachnotizId")).isTrue();
        assertThat(json.get("data").get("sprachnotizId").isNull()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendFailureIsNotThrown() {
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenThrow(new IllegalStateException("kein Broker"));

        assertThatCode(() -> publisher.publish(ProcessEvent.of("tour.started", "pi-1", "sub", Map.of())))
                .doesNotThrowAnyException();
    }
}
