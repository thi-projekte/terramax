package de.thi.terramax.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.thi.terramax.tour.StopTransitions.InvalidTransitionException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * Statusübergänge einer Tour ohne Datenbank.
 */
class TourTransitionsTest {

    private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-11-24T11:00:00+01:00");
    private static final OffsetDateTime T2 = OffsetDateTime.parse("2026-11-24T12:00:00+01:00");

    private static Tour tour(TourStatus status) {
        Tour tour = new Tour();
        tour.id = 1L;
        tour.status = status;
        return tour;
    }

    @Test
    void abortFromPlannedOrInProgress() {
        for (TourStatus status : new TourStatus[] { TourStatus.PLANNED, TourStatus.IN_PROGRESS }) {
            Tour tour = tour(status);
            assertTrue(TourTransitions.abort(tour, "Unwetter", T1));
            assertEquals(TourStatus.ABORTED, tour.status);
            assertEquals("Unwetter", tour.abortReason);
            assertEquals(T1, tour.abortedAt);
        }
    }

    @Test
    void abortWithoutReason() {
        Tour tour = tour(TourStatus.PLANNED);
        assertTrue(TourTransitions.abort(tour, null, T1));
        assertNull(tour.abortReason);
    }

    @Test
    void abortIsIdempotentAndKeepsFirstValues() {
        Tour tour = tour(TourStatus.IN_PROGRESS);
        TourTransitions.abort(tour, "Unwetter", T1);

        assertFalse(TourTransitions.abort(tour, "Unwetter", T2));
        assertFalse(TourTransitions.abort(tour, null, T2));
        assertEquals(T1, tour.abortedAt);
        assertThrows(InvalidTransitionException.class, () -> TourTransitions.abort(tour, "anders", T2));
    }

    @Test
    void completedOrDiscardedCannotBeAborted() {
        assertThrows(InvalidTransitionException.class,
                () -> TourTransitions.abort(tour(TourStatus.COMPLETED), null, T1));
        assertThrows(InvalidTransitionException.class,
                () -> TourTransitions.abort(tour(TourStatus.DISCARDED), null, T1));
    }

    @Test
    void finalStatusesCannotChange() {
        for (TourStatus from : new TourStatus[] { TourStatus.COMPLETED, TourStatus.DISCARDED, TourStatus.ABORTED }) {
            for (TourStatus to : new TourStatus[] { TourStatus.PLANNED, TourStatus.IN_PROGRESS, TourStatus.COMPLETED,
                    TourStatus.DISCARDED }) {
                Tour tour = tour(from);
                if (from == to) {
                    assertFalse(TourTransitions.changeStatus(tour, to));
                } else {
                    assertThrows(InvalidTransitionException.class, () -> TourTransitions.changeStatus(tour, to));
                }
            }
        }
    }

    @Test
    void openStatusesMayChange() {
        Tour tour = tour(TourStatus.PLANNED);
        assertTrue(TourTransitions.changeStatus(tour, TourStatus.IN_PROGRESS));
        assertTrue(TourTransitions.changeStatus(tour, TourStatus.COMPLETED));
    }

    @Test
    void abortedOnlyViaAbort() {
        assertThrows(IllegalArgumentException.class,
                () -> TourTransitions.changeStatus(tour(TourStatus.PLANNED), TourStatus.ABORTED));
    }
}
