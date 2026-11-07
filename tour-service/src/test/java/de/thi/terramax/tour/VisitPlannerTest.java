package de.thi.terramax.tour;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft die MVP-Regel ohne Quarkus: Auswahl nach ältestem Besuch, Reihenfolge nach Luftlinie.
 */
class VisitPlannerTest {

    private static final LocalDate TOUR_DATE = LocalDate.of(2026, 11, 10);

    // Start bei (48.0, 14.0); Kunden liegen auf einer Linie nach Norden, damit die Abstände eindeutig sind
    private static VisitPlanner planner(Customer... customers) {
        return new VisitPlanner(() -> List.of(customers), 48.0, 14.0);
    }

    private static Customer customer(String id, double latitude, LocalDate lastVisit) {
        return new Customer(id, "Betrieb " + id, "Ort " + id, new GeoPoint(latitude, 14.0), lastVisit);
    }

    private static List<String> ids(VisitPlan plan) {
        return plan.visits().stream().map(v -> v.customer().id()).toList();
    }

    @Test
    void selectsCustomersWithOldestVisitNeverVisitedFirst() {
        VisitPlan plan = planner(
                customer("recent", 48.1, LocalDate.of(2026, 10, 1)),
                customer("old", 48.2, LocalDate.of(2026, 1, 1)),
                customer("never", 48.3, null),
                customer("middle", 48.4, LocalDate.of(2026, 6, 1)))
                        .plan(TOUR_DATE, 3);

        // Reihenfolge prüft der nächste Test; hier zählt nur, wer ausgewählt wurde
        assertEquals(Set.of("old", "never", "middle"), Set.copyOf(ids(plan)));
    }

    @Test
    void ordersSelectedCustomersByNearestNeighbourFromStart() {
        VisitPlan plan = planner(
                customer("far", 48.3, null),
                customer("near", 48.1, null),
                customer("mid", 48.2, null))
                        .plan(TOUR_DATE, 5);

        assertEquals(List.of("near", "mid", "far"), ids(plan));
        assertEquals(List.of(1, 2, 3), plan.visits().stream().map(VisitPlan.PlannedVisit::position).toList());
    }

    @Test
    void calculatesDistancesAlongTheTour() {
        VisitPlan plan = planner(
                customer("a", 48.1, null),
                customer("b", 48.2, null))
                        .plan(TOUR_DATE, 5);

        // 0,1 Breitengrad sind etwa 11,1 km
        assertEquals(11.1, plan.visits().get(0).distanceFromPreviousKm());
        assertEquals(11.1, plan.visits().get(1).distanceFromPreviousKm());
        assertEquals(22.2, plan.totalDistanceKm());
    }

    @Test
    void skipsCustomersAlreadyVisitedOnOrAfterTourDate() {
        VisitPlan plan = planner(
                customer("today", 48.1, TOUR_DATE),
                customer("later", 48.2, TOUR_DATE.plusDays(3)),
                customer("before", 48.3, TOUR_DATE.minusDays(1)))
                        .plan(TOUR_DATE, 5);

        assertEquals(List.of("before"), ids(plan));
    }

    @Test
    void returnsFewerThanTwoVisitsWhenNotEnoughCustomers() {
        VisitPlan plan = planner(customer("only", 48.1, null)).plan(TOUR_DATE, 5);

        assertEquals(1, plan.visits().size());
    }

    @Test
    void mockDataAllowsPlanWithAtLeastTwoCustomers() {
        VisitPlan plan = new VisitPlanner(new MockCustomerRepository(), 48.1575, 14.0289).plan(TOUR_DATE, 5);

        assertEquals(5, plan.visits().size());
        assertTrue(plan.totalDistanceKm() > 0);
    }
}
