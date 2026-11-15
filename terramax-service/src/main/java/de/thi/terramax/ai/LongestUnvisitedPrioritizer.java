package de.thi.terramax.ai;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Platzhalter für die Priorisierung: Wer am längsten nicht besucht wurde, kommt zuerst; nie besuchte Kunden ganz vorne.
 * Bei Gleichstand entscheidet die Kunden-ID, damit das Ergebnis stabil ist.
 * <p>
 * TODO(#64-meeting): durch die abgestimmte Priorisierung ersetzen (siehe {@link VisitPrioritizer})
 */
@ApplicationScoped
@Typed(LongestUnvisitedPrioritizer.class)
public class LongestUnvisitedPrioritizer implements VisitPrioritizer {

    static final String NEVER_VISITED = "Platzhalter: noch nie besucht";
    static final String LONGEST_UNVISITED = "Platzhalter: am längsten nicht besucht";

    private static final Comparator<Candidate> ORDER = Comparator
            .comparing(Candidate::lastVisit, Comparator.nullsFirst(Comparator.<LocalDate> naturalOrder()))
            .thenComparingLong(Candidate::customerId);

    @Override
    public List<PrioritizedCustomer> prioritize(List<Candidate> candidates, PlanningConstraints constraints) {
        List<Candidate> sorted = candidates.stream().sorted(ORDER).limit(constraints.maxCustomers()).toList();
        List<PrioritizedCustomer> result = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            Candidate c = sorted.get(i);
            result.add(new PrioritizedCustomer(c.customerId(), i + 1,
                    c.lastVisit() == null ? NEVER_VISITED : LONGEST_UNVISITED));
        }
        return result;
    }
}
