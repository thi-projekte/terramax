package de.thi.terramax.tour;

import de.thi.terramax.ai.VisitPrioritizer;
import de.thi.terramax.ai.VisitPrioritizer.Candidate;
import de.thi.terramax.ai.VisitPrioritizer.PlanningConstraints;
import de.thi.terramax.ai.VisitPrioritizer.PrioritizedCustomer;
import de.thi.terramax.customer.Customer;
import de.thi.terramax.dealer.Dealer;
import de.thi.terramax.tour.RoutePlanner.Coordinates;
import de.thi.terramax.tour.RoutePlanner.PlannedStop;
import de.thi.terramax.tour.RoutePlanner.RouteStop;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Platzhalter für die Tourplanung: lädt die Kunden des Händlers mit dem Datum des letzten Besuchs, priorisiert sie und
 * plant die Route ab dem Händler. Letzter Besuch ist das spätere Datum aus besuchten Tour-Stopps und
 * Besuchsprotokollen.
 * <p>
 * TODO(#64-meeting): Übernahme des tour-service aus PR #53 mit Mohamed klären (siehe {@link TourPlanner})
 */
@ApplicationScoped
@DefaultBean
public class DefaultTourPlanner implements TourPlanner {

    private final VisitPrioritizer prioritizer;
    private final RoutePlanner routePlanner;
    private final EntityManager em;

    @Inject
    public DefaultTourPlanner(VisitPrioritizer prioritizer, RoutePlanner routePlanner, EntityManager em) {
        this.prioritizer = prioritizer;
        this.routePlanner = routePlanner;
        this.em = em;
    }

    @Override
    public TourPlanProposal propose(String dealerId, PlanningConstraints constraints) {
        Dealer dealer = Dealer.findById(dealerId);
        if (dealer == null) {
            throw new IllegalArgumentException("Unbekannter Händler: " + dealerId);
        }
        List<Customer> customers = Customer.listByDealer(dealerId);
        Map<Long, LocalDate> lastVisits = lastVisits(dealerId);
        List<Candidate> candidates = customers.stream()
                .map(c -> new Candidate(c.id, c.region, c.segment, lastVisits.get(c.id))).toList();

        List<PrioritizedCustomer> prioritized = prioritizer.prioritize(candidates, constraints);
        Map<Long, Customer> byId = customers.stream().collect(Collectors.toMap(c -> c.id, Function.identity()));
        List<RouteStop> stops = prioritized.stream().map(p -> byId.get(p.customerId()))
                .map(c -> new RouteStop(c.id, c.latitude, c.longitude)).toList();
        Coordinates start = dealer.latitude == null || dealer.longitude == null ? null
                : new Coordinates(dealer.latitude, dealer.longitude);

        return combine(constraints.date(), prioritized, routePlanner.plan(start, stops));
    }

    /** Führt Priorisierung (Rang, Begründung) und Route (Position, Entfernung, Fahrzeit) zusammen. */
    static TourPlanProposal combine(LocalDate date, List<PrioritizedCustomer> prioritized, List<PlannedStop> route) {
        Map<Long, PrioritizedCustomer> byId = prioritized.stream()
                .collect(Collectors.toMap(PrioritizedCustomer::customerId, Function.identity()));
        List<ProposedStop> stops = route.stream().map(s -> {
            PrioritizedCustomer p = byId.get(s.customerId());
            return new ProposedStop(s.customerId(), s.position(), p.rank(), p.reason(), s.distanceKm(), s.driveTime());
        }).toList();
        return new TourPlanProposal(date, stops);
    }

    private Map<Long, LocalDate> lastVisits(String dealerId) {
        Map<Long, LocalDate> result = new HashMap<>();
        em.createQuery("select tc.customer.id, max(tc.tour.tourDate) from TourCustomer tc "
                + "where tc.visited = true and tc.customer.dealer.id = ?1 group by tc.customer.id", Object[].class)
                .setParameter(1, dealerId).getResultList()
                .forEach(row -> result.merge((Long) row[0], (LocalDate) row[1], DefaultTourPlanner::later));
        em.createQuery("select p.customer.id, max(p.createdAt) from VisitorProtocol p "
                + "where p.customer.dealer.id = ?1 group by p.customer.id", Object[].class).setParameter(1, dealerId)
                .getResultList().forEach(row -> result.merge((Long) row[0],
                        ((OffsetDateTime) row[1]).toLocalDate(), DefaultTourPlanner::later));
        return result;
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
