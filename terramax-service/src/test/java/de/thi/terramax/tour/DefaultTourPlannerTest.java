package de.thi.terramax.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.thi.terramax.TestData;
import de.thi.terramax.ai.VisitPrioritizer.PlanningConstraints;
import de.thi.terramax.ai.VisitPrioritizer.PrioritizedCustomer;
import de.thi.terramax.tour.RoutePlanner.PlannedStop;
import de.thi.terramax.tour.TourPlanner.ProposedStop;
import de.thi.terramax.tour.TourPlanner.TourPlanProposal;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tourplanung mit den Platzhaltern (Priorisierung "am längsten nicht besucht", Nearest Neighbour) auf den Testdaten.
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
    void neverVisitedCustomersOfOwnDealerFirstThenRoutedFromDealer() {
        // huber hat Protokolle (also einen letzten Besuch), maier und ohneKoordinaten nie
        TourPlanProposal proposal = planner.propose(TestData.DEALER, new PlanningConstraints(DATE, 2));

        assertEquals(DATE, proposal.date());
        assertEquals(List.of(f.maier(), f.ohneKoordinaten()),
                proposal.stops().stream().map(ProposedStop::customerId).toList());
        assertNotNull(proposal.stops().get(0).distanceKm());
        assertNull(proposal.stops().get(1).distanceKm(), "ohne Koordinaten am Ende");
    }

    @Test
    void allCustomersOfOwnDealerOnly() {
        TourPlanProposal proposal = planner.propose(TestData.DEALER, new PlanningConstraints(DATE, 10));

        assertEquals(3, proposal.stops().size());
        assertEquals(List.of(1, 2, 3), proposal.stops().stream().map(ProposedStop::position).toList());
    }

    @Test
    void unknownDealerIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> planner.propose("D-999", new PlanningConstraints(DATE, 2)));
    }

    @Test
    void combineKeepsRouteOrderAndPriorityReason() {
        TourPlanProposal proposal = DefaultTourPlanner.combine(DATE,
                List.of(new PrioritizedCustomer(1, 1, "wichtig"), new PrioritizedCustomer(2, 2, "auch")),
                List.of(new PlannedStop(2, 1, 3.0, null), new PlannedStop(1, 2, 5.0, null)));

        assertEquals(List.of(new ProposedStop(2, 1, 2, "auch", 3.0, null), new ProposedStop(1, 2, 1, "wichtig", 5.0,
                null)), proposal.stops());
    }
}
