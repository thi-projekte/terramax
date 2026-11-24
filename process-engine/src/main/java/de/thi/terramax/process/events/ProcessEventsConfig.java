package de.thi.terramax.process.events;

import de.thi.terramax.process.terramax.TerraMaxClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cibseven.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Verdrahtet die Prozess-Events: Engine-Plugin plus Kafka- oder Log-Ausgabe, je nach {@code KAFKA_ENABLED}.
 */
@Configuration
public class ProcessEventsConfig {

    /**
     * @param kafkaEnabled
     *            {@code true} auf dem Server ({@code KAFKA_ENABLED}), sonst nur Log
     * @param topic
     *            Ziel-Topic, laut docs/kafka/README.md {@code terramax.process.events}
     */
    @ConfigurationProperties("terramax.events")
    public record ProcessEventsProperties(boolean kafkaEnabled, String topic) {
    }

    @Bean
    ProcessEnginePlugin processEventsPlugin(ProcessEventPublisher publisher, TerraMaxClient terraMax) {
        return new ProcessEventsPlugin(publisher, terraMax);
    }

    @Bean
    @ConditionalOnProperty(name = "terramax.events.kafka-enabled", havingValue = "true")
    ProcessEventPublisher kafkaProcessEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper, ProcessEventsProperties properties) {
        return new KafkaProcessEventPublisher(kafkaTemplate, objectMapper, properties.topic());
    }

    @Bean
    @ConditionalOnProperty(name = "terramax.events.kafka-enabled", havingValue = "false", matchIfMissing = true)
    ProcessEventPublisher loggingProcessEventPublisher() {
        return new LoggingProcessEventPublisher();
    }
}
