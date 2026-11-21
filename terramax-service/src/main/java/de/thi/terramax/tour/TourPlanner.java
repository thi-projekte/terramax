package de.thi.terramax.tour;

import de.thi.terramax.ai.VisitPrioritizer.PlanningConstraints;
import de.thi.terramax.tour.RoutePlanner.Coordinates;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Erstellt einen Planvorschlag für eine Tour: erst {@link de.thi.terramax.ai.VisitPrioritizer} (Auswahl), dann
 * {@link RoutePlanner} (Reihenfolge). Der Vorschlag wird nicht gespeichert; {@code GET /tours/plan} liefert ihn im
 * Format von {@code TourPlan} der process-engine. Umsetzung: {@link DefaultTourPlanner} (Regel aus dem tour-service, PR
 * #53).
 */
public interface TourPlanner {

    /**
     * @throws IllegalArgumentException
     *             wenn der Händler unbekannt ist
     */
    TourPlanProposal propose(String dealerId, PlanningConstraints constraints);

    /**
     * @param start
     *            Startpunkt (Standort des Händlers); {@code null}, wenn der Händler keine Koordinaten hat
     */
    record TourPlanProposal(LocalDate date, Coordinates start, List<ProposedStop> stops) {
    }

    /**
     * Ein Kunde im Vorschlag. {@code position} ist die Fahrreihenfolge, {@code rank} die Wichtigkeit laut
     * Priorisierung.
     *
     * @param lastVisit
     *            letzter dokumentierter Besuch oder letztes Protokoll; {@code null}, wenn nie besucht
     * @param distanceKm
     *            Luftlinie zum vorherigen Halt (beim ersten zum Start); {@code null} ohne Startpunkt
     */
    record ProposedStop(long customerId, int position, int rank, String reason, LocalDate lastVisit,
            Double distanceKm, Duration driveTime) {
    }
}
