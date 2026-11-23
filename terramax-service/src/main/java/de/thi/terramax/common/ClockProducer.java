package de.thi.terramax.common;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.ZoneId;

/**
 * Uhr, aus der "heute" bestimmt wird (Zeitzone Wien, wie im tour-service). Als eigene Bean, damit Tests ein festes
 * Datum einsetzen können.
 */
public class ClockProducer {

    public static final ZoneId ZONE = ZoneId.of("Europe/Vienna");

    @Produces
    @Singleton
    @DefaultBean
    Clock clock() {
        return Clock.system(ZONE);
    }
}
