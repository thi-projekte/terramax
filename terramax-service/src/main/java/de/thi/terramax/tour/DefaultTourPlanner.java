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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tourplanung nach der MVP-Regel des tour-service ({@code VisitPlanner} aus PR #53, Service Task "Besuchsplan
 * erzeugen"), jetzt mit den Kunden aus der Datenbank statt Mock-Kunden:
 * <ol>
 * <li><b>Kandidaten:</b> Kunden des Händlers mit Koordinaten. Wer am Tourtag oder danach schon besucht wurde, fällt
 * heraus. Letzter Besuch ist das spätere Datum aus dokumentierten Halten (Tourdatum) und Besuchsprotokollen.</li>
 * <li><b>Auswahl:</b> {@link VisitPrioritizer}, Standard {@code LongestUnvisitedPrioritizer}: nie besuchte zuerst, dann
 * am längsten nicht besuchte.</li>
 * <li><b>Reihenfolge:</b> {@link RoutePlanner}, Standard {@link NearestNeighbourRoutePlanner}: Luftlinie ab dem
 * Standort des Händlers, immer zum nächstgelegenen offenen Kunden.</li>
 * </ol>
 * Kunden ohne Koordinaten kommen nicht in den Plan, weil sich für sie keine Route berechnen lässt.
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
        Map<Long, LocalDate> lastVisits = LastVisits.byCustomer(em, dealerId);
        List<Customer> customers = Customer.listByDealer(dealerId).stream()
                .filter(c -> c.latitude != null && c.longitude != null)
                .filter(c -> isDue(lastVisits.get(c.id), constraints.date())).toList();
        List<Candidate> candidates = customers.stream()
                .map(c -> new Candidate(c.id, c.region, c.segment, lastVisits.get(c.id))).toList();

        List<PrioritizedCustomer> prioritized = prioritizer.prioritize(candidates, constraints);
        Map<Long, Customer> byId = customers.stream().collect(Collectors.toMap(c -> c.id, Function.identity()));
        List<RouteStop> stops = prioritized.stream().map(p -> byId.get(p.customerId()))
                .map(c -> new RouteStop(c.id, c.latitude, c.longitude)).toList();
        Coordinates start = dealer.latitude == null || dealer.longitude == null ? null
                : new Coordinates(dealer.latitude, dealer.longitude);

        return combine(constraints.date(), start, prioritized, lastVisits, routePlanner.plan(start, stops));
    }

    /** Noch nie besucht oder zuletzt vor dem Tourtag. */
    static boolean isDue(LocalDate lastVisit, LocalDate tourDate) {
        return lastVisit == null || lastVisit.isBefore(tourDate);
    }

    /** Führt Priorisierung (Rang, Begründung) und Route (Position, Entfernung, Fahrzeit) zusammen. */
    static TourPlanProposal combine(LocalDate date, Coordinates start, List<PrioritizedCustomer> prioritized,
            Map<Long, LocalDate> lastVisits, List<PlannedStop> route) {
        Map<Long, PrioritizedCustomer> byId = prioritized.stream()
                .collect(Collectors.toMap(PrioritizedCustomer::customerId, Function.identity()));
        List<ProposedStop> stops = route.stream().map(s -> {
            PrioritizedCustomer p = byId.get(s.customerId());
            return new ProposedStop(s.customerId(), s.position(), p.rank(), p.reason(),
                    lastVisits.get(s.customerId()), s.distanceKm(), s.driveTime());
        }).toList();
        return new TourPlanProposal(date, start, stops);
    }

}
