package de.thi.terramax.tour;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Statusübergänge eines Halts. Jeder Übergang ist idempotent: Ein wiederholter Aufruf mit demselben Ziel ändert nichts
 * und gilt als Erfolg (die Engine darf Service Tasks bei Fehlern wiederholen). Ungültige Übergänge werfen
 * {@link InvalidTransitionException} (API: 409).
 * <table>
 * <caption>Übergänge (alle anderen: 409)</caption>
 * <tr>
 * <th>Aufruf</th>
 * <th>OFFEN</th>
 * <th>OFFEN, begonnen</th>
 * <th>DOKUMENTIERT</th>
 * <th>UEBERSPRUNGEN</th>
 * </tr>
 * <tr>
 * <td>start</td>
 * <td>setzt startedAt</td>
 * <td>unverändert</td>
 * <td>409</td>
 * <td>409</td>
 * </tr>
 * <tr>
 * <td>skip</td>
 * <td>UEBERSPRUNGEN</td>
 * <td>UEBERSPRUNGEN</td>
 * <td>409</td>
 * <td>unverändert bei gleichem Grund, sonst 409</td>
 * </tr>
 * <tr>
 * <td>complete</td>
 * <td>DOKUMENTIERT</td>
 * <td>DOKUMENTIERT</td>
 * <td>unverändert</td>
 * <td>409</td>
 * </tr>
 * </table>
 * Erledigte Halte ({@code DOKUMENTIERT}, {@code UEBERSPRUNGEN}) lassen sich nicht mehr ändern. Auf Touren mit Status
 * {@code completed} oder {@code discarded} ist kein Übergang mehr möglich. Der erste Übergang setzt eine Tour von
 * {@code planned} auf {@code in_progress}.
 */
public final class StopTransitions {

    private StopTransitions() {
    }

    /** Ungültiger Statusübergang. */
    public static class InvalidTransitionException extends RuntimeException {

        InvalidTransitionException(String message) {
            super(message);
        }
    }

    /**
     * Besuch beginnen. Idempotent, solange der Halt offen ist; der erste Zeitpunkt bleibt.
     *
     * @return {@code true}, wenn sich etwas geändert hat
     */
    public static boolean start(TourCustomer stop, OffsetDateTime at) {
        requireTourOpen(stop);
        if (stop.status.isDone()) {
            throw conflict(stop, "kann nicht mehr begonnen werden");
        }
        if (stop.startedAt != null) {
            return false;
        }
        stop.startedAt = at;
        markTourInProgress(stop.tour);
        return true;
    }

    /**
     * Besuch überspringen. Idempotent bei gleichem Grund; ein anderer Grund für einen schon übersprungenen Halt ist ein
     * Konflikt.
     *
     * @param reason
     *            Grund, bereits geprüft (nicht leer)
     *
     * @return {@code true}, wenn sich etwas geändert hat
     */
    public static boolean skip(TourCustomer stop, String reason, OffsetDateTime at) {
        requireTourOpen(stop);
        if (stop.status == StopStatus.UEBERSPRUNGEN) {
            if (Objects.equals(stop.skipReason, reason)) {
                return false;
            }
            throw conflict(stop, "wurde bereits mit einem anderen Grund übersprungen");
        }
        if (stop.status == StopStatus.DOKUMENTIERT) {
            throw conflict(stop, "ist bereits dokumentiert und kann nicht übersprungen werden");
        }
        stop.status = StopStatus.UEBERSPRUNGEN;
        stop.skipReason = reason;
        stop.finishedAt = at;
        markTourInProgress(stop.tour);
        return true;
    }

    /**
     * Besuch als dokumentiert abschließen. Idempotent; geht auch ohne vorheriges {@link #start}.
     *
     * @return {@code true}, wenn sich etwas geändert hat
     */
    public static boolean complete(TourCustomer stop, OffsetDateTime at) {
        requireTourOpen(stop);
        if (stop.status == StopStatus.DOKUMENTIERT) {
            return false;
        }
        if (stop.status == StopStatus.UEBERSPRUNGEN) {
            throw conflict(stop, "wurde übersprungen und kann nicht mehr dokumentiert werden");
        }
        if (stop.startedAt != null && at.isBefore(stop.startedAt)) {
            throw new IllegalArgumentException("Der Abschluss liegt vor dem Beginn des Besuchs.");
        }
        stop.status = StopStatus.DOKUMENTIERT;
        stop.finishedAt = at;
        markTourInProgress(stop.tour);
        return true;
    }

    private static void requireTourOpen(TourCustomer stop) {
        TourStatus status = stop.tour.status;
        if (status == TourStatus.COMPLETED || status == TourStatus.DISCARDED) {
            throw new InvalidTransitionException(
                    "Tour " + stop.tour.id + " ist " + status.getDbValue() + ", Halte sind nicht mehr änderbar.");
        }
    }

    private static void markTourInProgress(Tour tour) {
        if (tour.status == TourStatus.PLANNED) {
            tour.status = TourStatus.IN_PROGRESS;
        }
    }

    private static InvalidTransitionException conflict(TourCustomer stop, String what) {
        return new InvalidTransitionException(
                "Halt " + stop.customer.id + " (" + stop.status + ") " + what + ".");
    }
}
