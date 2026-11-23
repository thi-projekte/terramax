package de.thi.terramax.notification;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class EventConsumerTest {

    // Beispiel aus docs/kafka/README.md, mit eigener id und userid je Test
    private static final String EVENT = """
            {
              "specversion": "1.0",
              "id": "%s",
              "type": "de.thi.terramax.process.task.created",
              "source": "/process-engine",
              "time": "2026-11-12T08:15:30Z",
              "subject": "tour-1",
              "userid": "%s",
              "datacontenttype": "application/json",
              "data": { "name": "Besuchsplan prüfen" }
            }
            """;

    @Inject
    EventConsumer consumer;

    @Inject
    NotificationStore store;

    @Test
    void eventBecomesNotificationForItsUser() {
        consumer.handle(EVENT.formatted("e-1", "user-carla"));

        List<Notification> list = store.forUser("user-carla");
        assertEquals(1, list.size());
        assertEquals("e-1", list.get(0).id());
        assertEquals("de.thi.terramax.process.task.created", list.get(0).type());
        assertEquals("Besuchsplan prüfen", list.get(0).data().get("name"));
    }

    @Test
    void duplicateEventIsStoredOnce() {
        consumer.handle(EVENT.formatted("e-2", "user-dora"));
        consumer.handle(EVENT.formatted("e-2", "user-dora"));

        assertEquals(1, store.forUser("user-dora").size());
    }

    @Test
    void invalidEventIsSkipped() {
        consumer.handle("kein json");
        consumer.handle("{}");

        assertEquals(0, store.forUser("user-nobody").size());
    }
}
