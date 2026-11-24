package de.thi.terramax.process.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Ein Prozess-Event als CloudEvent 1.0 (structured mode). Aufbau und Typen sind in docs/kafka/README.md festgelegt.
 * Feldnamen entsprechen genau den CloudEvents-Attributen, damit Jackson das JSON ohne Zusatzangaben erzeugt.
 *
 * @param subject
 *            {@code processInstanceId}, zugleich Kafka-Key
 * @param userid
 *            Keycloak-{@code sub} des Sales Professional, an den sich das Event richtet
 * @param data
 *            Nutzdaten je Typ, enthält immer {@code processInstanceId}
 */
public record ProcessEvent(String specversion, String id, String type, String source, String time, String subject,
        String userid, String datacontenttype, Map<String, Object> data) {

    public static final String TYPE_PREFIX = "de.thi.terramax.process.";
    public static final String SOURCE = "/process-engine";

    /**
     * Neues Event mit eindeutiger ID und aktuellem Zeitpunkt.
     *
     * @param type
     *            Typ ohne Präfix, z. B. {@code tour.started}
     */
    public static ProcessEvent of(String type, String processInstanceId, String userId, Map<String, Object> data) {
        return new ProcessEvent("1.0", UUID.randomUUID().toString(), TYPE_PREFIX + type, SOURCE,
                Instant.now().toString(), processInstanceId, userId, "application/json", data);
    }
}
