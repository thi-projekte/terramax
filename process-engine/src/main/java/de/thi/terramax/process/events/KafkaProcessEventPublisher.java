package de.thi.terramax.process.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;

/**
 * Publiziert Prozess-Events auf {@code terramax.process.events}: Key ist die {@code processInstanceId}, Value das
 * komplette CloudEvent als JSON, Header {@code content-type: application/cloudevents+json} (docs/kafka/README.md).
 */
public class KafkaProcessEventPublisher implements ProcessEventPublisher {

    static final String CONTENT_TYPE = "application/cloudevents+json";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaProcessEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaProcessEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
            String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public void publish(ProcessEvent event) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.subject(),
                    objectMapper.writeValueAsString(event));
            record.headers().add("content-type", CONTENT_TYPE.getBytes(StandardCharsets.UTF_8));
            kafkaTemplate.send(record).whenComplete((result, error) -> {
                if (error != null) {
                    LOG.warn("Prozess-Event {} ({}) nicht gesendet: {}", event.type(), event.id(), error.toString());
                }
            });
        } catch (Exception e) {
            LOG.warn("Prozess-Event {} ({}) nicht gesendet: {}", event.type(), event.id(), e.toString());
        }
    }
}
