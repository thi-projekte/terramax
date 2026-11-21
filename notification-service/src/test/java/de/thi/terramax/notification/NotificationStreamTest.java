package de.thi.terramax.notification;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationStreamTest {

    @Test
    void userReceivesOnlyOwnNotifications() {
        NotificationStream stream = new NotificationStream();
        List<Notification> received = new ArrayList<>();
        stream.forUser("user-alice").subscribe().with(received::add);

        stream.publish("user-alice", new Notification("n-1", "test.type", Instant.now(), "tour-1", Map.of()));
        stream.publish("user-bob", new Notification("n-2", "test.type", Instant.now(), "tour-2", Map.of()));

        assertEquals(1, received.size());
        assertEquals("n-1", received.get(0).id());
    }
}
