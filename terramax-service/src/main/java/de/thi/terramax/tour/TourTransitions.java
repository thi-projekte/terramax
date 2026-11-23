package de.thi.terramax.tour;

import de.thi.terramax.tour.StopTransitions.InvalidTransitionException;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Statusübergänge einer Tour. {@code completed}, {@code discarded} und {@code aborted} sind endgültig: Ein Wechsel in
 * einen anderen Status ergibt {@link InvalidTransitionException} (API: 409), derselbe Status ist idempotent.
 * <ul>
 * <li>{@link #changeStatus}: {@code PATCH /tours/{id}} mit {@code planned}, {@code in_progress}, {@code completed} oder
 * {@code discarded}. {@code aborted} geht nur über {@link #abort}.</li>
 * <li>{@link #abort}: {@code POST /tours/{id}/abort}, aus {@code planned} oder {@code in_progress}. Offene Halte
 * bleiben {@code OFFEN}.</li>
 * </ul>
 */
public final class TourTransitions {

    private TourTransitions() {
    }

    /**
     * @return {@code true}, wenn sich der Status geändert hat
     *
     * @throws IllegalArgumentException
     *             wenn {@code target} {@code aborted} ist (dafür gibt es {@link #abort})
     */
    public static boolean changeStatus(Tour tour, TourStatus target) {
        if (target == TourStatus.ABORTED) {
            throw new IllegalArgumentException("Abbrechen geht nur über POST /tours/" + tour.id + "/abort.");
        }
        if (tour.status == target) {
            return false;
        }
        if (tour.status.isFinal()) {
            throw new InvalidTransitionException("Tour " + tour.id + " ist " + tour.status.getDbValue()
                    + " und kann nicht mehr " + target.getDbValue() + " werden.");
        }
        tour.status = target;
        return true;
    }

    /**
     * Tour abbrechen. Idempotent: Eine abgebrochene Tour bleibt unverändert, wenn kein Grund oder derselbe Grund kommt.
     *
     * @param reason
     *            optionaler Grund, bereits geprüft; {@code null} ohne Grund
     *
     * @return {@code true}, wenn sich etwas geändert hat
     */
    public static boolean abort(Tour tour, String reason, OffsetDateTime at) {
        if (tour.status == TourStatus.ABORTED) {
            if (reason == null || Objects.equals(reason, tour.abortReason)) {
                return false;
            }
            throw new InvalidTransitionException(
                    "Tour " + tour.id + " wurde bereits mit einem anderen Grund abgebrochen.");
        }
        if (tour.status.isFinal()) {
            throw new InvalidTransitionException(
                    "Tour " + tour.id + " ist " + tour.status.getDbValue() + " und kann nicht abgebrochen werden.");
        }
        tour.status = TourStatus.ABORTED;
        tour.abortReason = reason;
        tour.abortedAt = at;
        return true;
    }
}
