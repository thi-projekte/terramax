package de.thi.terramax.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.thi.terramax.TestData;
import de.thi.terramax.ai.VisitPrioritizer.PlanningConstraints;
import de.thi.terramax.ai.VisitPrioritizer.PrioritizedCustomer;
import de.thi.terramax.tour.RoutePlanner.Coordinates;
import de.thi.terramax.tour.RoutePlanner.PlannedStop;
import de.thi.terramax.tour.TourPlanner.ProposedStop;
import de.thi.terramax.tour.TourPlanner.TourPlanProposal;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tourplanung nach der Regel aus dem tour-service (PR #53) auf den Testdaten: Priorisierung "am längsten nicht
 * besucht", Reihenfolge Nearest Neighbour ab Händler, Kunden ohne Koordinaten fallen weg.
 */
@QuarkusTest
class DefaultTourPlannerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 12, 1);

    @Inject
    TestData testData;

    @Inject
    TourPlanner planner;

    private TestData.Fixture f;

    @BeforeEach
    void setUp() {
        f = testData.reset();
    }

    @Test
    void neverVisitedFirstButRoutedByDistanceFromDealer() {
        // huber hat Protokolle (also einen letzten Besuch), maier nie; ohneKoordinaten lässt sich nicht routen
        TourPlanProposal proposal = planner.propose(TestData.DEALER, new PlanningConstraints(DATE, 10));

        assertEquals(DATE, proposal.date());
        assertEquals(new Coordinates(48.16, 14.03), proposal.start());
        // huber liegt näher am Händler als maier, deshalb zuerst, obwohl maier wichtiger ist
        assertEquals(List.of(f.huber(), f.maier()), proposal.stops().stream().map(ProposedStop::customerId).toList());
        assertEquals(List.of(2, 1), proposal.stops().stream().map(ProposedStop::rank).toList());
        assertEquals(List.of(1, 2), proposal.stops().stream().map(ProposedStop::position).toList());
        proposal.stops().forEach(s -> assertNotNull(s.distanceKm()));
        assertNotNull(proposal.stops().get(0).lastVisit());
        assertNull(proposal.stops().get(1).lastVisit());
    }

    @Test
    void maxCustomersPicksLongestUnvisited() {
        TourPlanProposal proposal = planner.propose(TestData.DEALER, new PlanningConstraints(DATE, 1));

        assertEquals(List.of(f.maier()), proposal.stops().stream().map(ProposedStop::customerId).toList());
    }

    @Test
    void customersVisitedOnOrAfterTourDateAreLeftOut() {
        // huber hat heute (nach dem 20.11.2026) ein Protokoll bekommen
        TourPlanProposal proposal = planner.propose(TestData.DEALER,
                new PlanningConstraints(LocalDate.of(2026, 11, 20), 10));

        assertEquals(List.of(f.maier()), proposal.stops().stream().map(ProposedStop::customerId).toList());
    }

    @Test
    void documentedStopCountsAsVisit() {
        testData.documentStop(f.ownTour(), f.maier());

        TourPlanProposal proposal = planner.propose(TestData.DEALER, new PlanningConstraints(DATE, 10));

        ProposedStop maier = proposal.stops().stream().filter(s -> s.customerId() == f.maier()).findFirst()
                .orElseThrow();
        assertEquals(LocalDate.of(2026, 11, 20), maier.lastVisit());
    }

    @Test
    void unknownDealerIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> planner.propose("D-999", new PlanningConstraints(DATE, 2)));
    }

    @Test
    void isDueOnlyBeforeTourDate() {
        assertTrue(DefaultTourPlanner.isDue(null, DATE));
        assertTrue(DefaultTourPlanner.isDue(DATE.minusDays(1), DATE));
        assertFalse(DefaultTourPlanner.isDue(DATE, DATE));
        assertFalse(DefaultTourPlanner.isDue(DATE.plusDays(1), DATE));
    }

    @Test
    void combineKeepsRouteOrderAndPriorityReason() {
        TourPlanProposal proposal = DefaultTourPlanner.combine(DATE, null,
                List.of(new PrioritizedCustomer(1, 1, "wichtig"), new PrioritizedCustomer(2, 2, "auch")),
                Map.of(2L, LocalDate.of(2026, 5, 1)),
                List.of(new PlannedStop(2, 1, 3.0, null), new PlannedStop(1, 2, 5.0, null)));

        assertEquals(List.of(new ProposedStop(2, 1, 2, "auch", LocalDate.of(2026, 5, 1), 3.0, null),
                new ProposedStop(1, 2, 1, "wichtig", null, 5.0, null)), proposal.stops());
    }
}
