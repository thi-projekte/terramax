package de.thi.terramax.notification;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Verteilt neue Benachrichtigungen an alle gerade verbundenen Browser (SSE). Wer nicht verbunden ist, bekommt sie
 * später über GET /notifications.
 */
@ApplicationScoped
public class NotificationStream {

    private record Entry(String userId, Notification notification) {
    }

    private final BroadcastProcessor<Entry> processor = BroadcastProcessor.create();

    public synchronized void publish(String userId, Notification notification) {
        processor.onNext(new Entry(userId, notification));
    }

    public Multi<Notification> forUser(String userId) {
        return processor.filter(entry -> entry.userId().equals(userId)).map(Entry::notification);
    }
}
