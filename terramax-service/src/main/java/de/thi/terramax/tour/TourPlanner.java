package de.thi.terramax.tour;

import de.thi.terramax.ai.VisitPrioritizer.PlanningConstraints;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Erstellt einen Planvorschlag für eine Tour: erst {@link de.thi.terramax.ai.VisitPrioritizer}, dann
 * {@link RoutePlanner}. Der Vorschlag wird nicht gespeichert. Platzhalter: {@link DefaultTourPlanner}.
 * <p>
 * Es gibt bewusst noch keinen Endpunkt {@code GET /tours/plan}.
 * <p>
 * TODO(#64-meeting): Übernahme des tour-service aus PR #53 mit Mohamed klären; dann GET /tours/plan im bisherigen
 * Format für die process-engine anbieten
 */
public interface TourPlanner {

    /**
     * @throws IllegalArgumentException
     *             wenn der Händler unbekannt ist
     */
    TourPlanProposal propose(String dealerId, PlanningConstraints constraints);

    record TourPlanProposal(LocalDate date, List<ProposedStop> stops) {
    }

    /**
     * Ein Kunde im Vorschlag. {@code position} ist die Fahrreihenfolge, {@code rank} die Wichtigkeit laut
     * Priorisierung.
     */
    record ProposedStop(long customerId, int position, int rank, String reason, Double distanceKm,
            Duration driveTime) {
    }
}
