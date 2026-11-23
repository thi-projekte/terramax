package de.thi.terramax.notification;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hält die Benachrichtigungen je Benutzer im Arbeitsspeicher. Nach einem Neustart ist der Speicher leer.
 */
@ApplicationScoped
public class NotificationStore {

    private final Map<String, List<Notification>> byUser = new HashMap<>();

    public synchronized boolean add(String userId, Notification notification) {
        List<Notification> list = byUser.computeIfAbsent(userId, key -> new ArrayList<>());
        boolean known = list.stream().anyMatch(n -> n.id().equals(notification.id()));
        if (known) {
            return false;
        }
        list.add(notification);
        return true;
    }

    public synchronized List<Notification> forUser(String userId) {
        return List.copyOf(byUser.getOrDefault(userId, List.of()));
    }
}
