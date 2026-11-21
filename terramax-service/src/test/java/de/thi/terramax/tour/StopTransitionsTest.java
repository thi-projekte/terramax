package de.thi.terramax.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.thi.terramax.customer.Customer;
import de.thi.terramax.tour.StopTransitions.InvalidTransitionException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Statusübergänge ohne Datenbank.
 */
class StopTransitionsTest {

    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-11-24T09:00:00+01:00");
    private static final OffsetDateTime T2 = OffsetDateTime.parse("2026-11-24T10:00:00+01:00");

    private TourCustomer stop;

    @BeforeEach
    void setUp() {
        Tour tour = new Tour();
        tour.id = 1L;
        Customer customer = new Customer();
        customer.id = 7L;
        stop = TourCustomer.of(tour, customer, 1);
    }

    @Test
    void startSetsTimeOnceAndMarksTourInProgress() {
        assertTrue(StopTransitions.start(stop, T1));
        assertFalse(StopTransitions.start(stop, T2));
        assertEquals(T1, stop.startedAt);
        assertEquals(StopStatus.OFFEN, stop.status);
        assertEquals(TourStatus.IN_PROGRESS, stop.tour.status);
    }

    @Test
    void completeFromOpenOrStarted() {
        assertTrue(StopTransitions.complete(stop, T2));
        assertFalse(StopTransitions.complete(stop, T1));
        assertEquals(StopStatus.DOKUMENTIERT, stop.status);
        assertEquals(T2, stop.finishedAt);
    }

    @Test
    void completeBeforeStartIsRejected() {
        StopTransitions.start(stop, T2);
        assertThrows(IllegalArgumentException.class, () -> StopTransitions.complete(stop, T1));
    }

    @Test
    void skipKeepsFirstReason() {
        assertTrue(StopTransitions.skip(stop, "krank", T1));
        assertFalse(StopTransitions.skip(stop, "krank", T2));
        assertEquals(T1, stop.finishedAt);
        assertThrows(InvalidTransitionException.class, () -> StopTransitions.skip(stop, "anders", T2));
    }

    @Test
    void doneStopsAreFinal() {
        StopTransitions.complete(stop, T1);
        assertThrows(InvalidTransitionException.class, () -> StopTransitions.skip(stop, "x", T2));
        assertThrows(InvalidTransitionException.class, () -> StopTransitions.start(stop, T2));

        setUp();
        StopTransitions.skip(stop, "x", T1);
        assertThrows(InvalidTransitionException.class, () -> StopTransitions.complete(stop, T2));
        assertThrows(InvalidTransitionException.class, () -> StopTransitions.start(stop, T2));
    }

    @Test
    void finishedOrDiscardedTourAllowsNoTransition() {
        for (TourStatus status : new TourStatus[] { TourStatus.COMPLETED, TourStatus.DISCARDED }) {
            setUp();
            stop.tour.status = status;
            assertThrows(InvalidTransitionException.class, () -> StopTransitions.start(stop, T1));
            assertThrows(InvalidTransitionException.class, () -> StopTransitions.complete(stop, T1));
            assertThrows(InvalidTransitionException.class, () -> StopTransitions.skip(stop, "x", T1));
        }
    }
}
