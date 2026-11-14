package de.thi.terramax.tour;

import java.time.LocalDate;
import java.util.List;

/**
 * Vom System vorgeschlagener Besuchsplan für einen Tag (Prozessvariable {@code besuchsplan} im BPMN-Modell
 * "Besuchstour").
 *
 * @param start
 *            Ausgangspunkt der Tour (Standort des Händlers)
 * @param visits
 *            Besuche in der festgelegten Reihenfolge
 * @param totalDistanceKm
 *            Summe der Luftlinien vom Start über alle Besuche (ohne Rückweg)
 */
public record VisitPlan(LocalDate date, GeoPoint start, List<PlannedVisit> visits, double totalDistanceKm) {

    /**
     * Ein Besuch im Plan.
     *
     * @param position
     *            Reihenfolge, beginnend bei 1
     * @param distanceFromPreviousKm
     *            Luftlinie vom vorherigen Halt (beim ersten Besuch vom Start)
     */
    public record PlannedVisit(int position, Customer customer, double distanceFromPreviousKm) {
    }
}
