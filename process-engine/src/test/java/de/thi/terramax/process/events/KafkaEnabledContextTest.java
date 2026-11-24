package de.thi.terramax.process.events;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mit {@code KAFKA_ENABLED=true} (wie auf dem Server) startet die Anwendung und nutzt den Kafka-Publisher. Ein Broker
 * ist dafür nicht nötig, verbunden wird erst beim ersten Event.
 */
@SpringBootTest(properties = "terramax.events.kafka-enabled=true")
@ActiveProfiles("test")
class KafkaEnabledContextTest {

    @Autowired
    ProcessEventPublisher publisher;

    @Test
    void usesKafkaPublisher() {
        assertThat(publisher).isInstanceOf(KafkaProcessEventPublisher.class);
    }
}
