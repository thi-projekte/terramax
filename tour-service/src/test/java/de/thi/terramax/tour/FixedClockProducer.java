package de.thi.terramax.tour;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Ersetzt in allen Quarkus-Tests die echte Uhr durch ein festes Datum, damit Tests ohne {@code date}-Parameter nicht
 * vom Tag abhängen, an dem sie laufen.
 */
public class FixedClockProducer {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final ZoneId VIENNA = ZoneId.of("Europe/Vienna");

    @Produces
    @Singleton
    @Alternative
    @Priority(1)
    Clock fixedClock() {
        return Clock.fixed(TODAY.atStartOfDay(VIENNA).toInstant(), VIENNA);
    }
}
