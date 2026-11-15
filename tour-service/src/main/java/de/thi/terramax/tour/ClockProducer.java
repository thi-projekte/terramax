package de.thi.terramax.tour;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Liefert die Uhr, aus der "heute" bestimmt wird (Zeitzone Wien). Als eigene Bean, damit Tests ein festes Datum
 * einsetzen können und nicht vom echten Tagesdatum abhängen.
 */
public class ClockProducer {

    @Produces
    @Singleton
    Clock clock() {
        return Clock.system(ZoneId.of("Europe/Vienna"));
    }
}
