package de.thi.terramax.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * Liest die Events aus den beiden Kafka-Topics und legt sie als Benachrichtigung beim Empfänger (userid) ab.
 */
@ApplicationScoped
public class EventConsumer {

    private static final Logger LOG = Logger.getLogger(EventConsumer.class);

    @Inject
    ObjectMapper mapper;

    @Inject
    NotificationStore store;

    @Inject
    NotificationStream stream;

    @Incoming("process-events")
    public void onProcessEvent(String json) {
        handle(json);
    }

    @Incoming("speech-events")
    public void onSpeechEvent(String json) {
        handle(json);
    }

    void handle(String json) {
        try {
            CloudEvent event = mapper.readValue(json, CloudEvent.class);
            if (event.id() == null || event.userid() == null) {
                LOG.warn("Event ohne id oder userid übersprungen");
                return;
            }
            Notification notification = new Notification(event.id(), event.type(), event.time(), event.subject(),
                    event.data());
            if (store.add(event.userid(), notification)) {
                stream.publish(event.userid(), notification);
            }
        } catch (JsonProcessingException e) {
            LOG.warn("Ungültiges Event übersprungen");
        }
    }
}
