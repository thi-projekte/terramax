package de.thi.terramax.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.thi.terramax.ai.VisitPrioritizer.Candidate;
import de.thi.terramax.ai.VisitPrioritizer.PlanningConstraints;
import de.thi.terramax.ai.VisitPrioritizer.PrioritizedCustomer;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LongestUnvisitedPrioritizerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);

    private final LongestUnvisitedPrioritizer prioritizer = new LongestUnvisitedPrioritizer();

    private static Candidate candidate(long id, LocalDate lastVisit) {
        return new Candidate(id, "Marchfeld", "Ackerbau", lastVisit);
    }

    @Test
    void neverVisitedFirstThenLongestAgo() {
        List<PrioritizedCustomer> result = prioritizer.prioritize(List.of(candidate(1, LocalDate.of(2026, 10, 1)),
                candidate(2, null), candidate(3, LocalDate.of(2026, 6, 1)), candidate(4, LocalDate.of(2026, 11, 1))),
                new PlanningConstraints(DATE, 10));

        assertEquals(List.of(2L, 3L, 1L, 4L), result.stream().map(PrioritizedCustomer::customerId).toList());
        assertEquals(List.of(1, 2, 3, 4), result.stream().map(PrioritizedCustomer::rank).toList());
        assertEquals(LongestUnvisitedPrioritizer.NEVER_VISITED, result.get(0).reason());
        assertEquals(LongestUnvisitedPrioritizer.LONGEST_UNVISITED, result.get(1).reason());
    }

    @Test
    void tieIsBrokenByCustomerId() {
        LocalDate same = LocalDate.of(2026, 9, 1);
        List<PrioritizedCustomer> result = prioritizer.prioritize(
                List.of(candidate(7, same), candidate(3, null), candidate(5, same), candidate(1, null)),
                new PlanningConstraints(DATE, 10));

        assertEquals(List.of(1L, 3L, 5L, 7L), result.stream().map(PrioritizedCustomer::customerId).toList());
    }

    @Test
    void limitsToMaxCustomers() {
        List<PrioritizedCustomer> result = prioritizer.prioritize(
                List.of(candidate(1, null), candidate(2, null), candidate(3, null)), new PlanningConstraints(DATE, 2));

        assertEquals(List.of(1L, 2L), result.stream().map(PrioritizedCustomer::customerId).toList());
    }

    @Test
    void noCandidatesGiveEmptyResult() {
        assertEquals(List.of(), prioritizer.prioritize(List.of(), new PlanningConstraints(DATE, 3)));
    }
}
