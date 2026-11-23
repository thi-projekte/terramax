package de.thi.terramax.tour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.thi.terramax.tour.RoutePlanner.Coordinates;
import de.thi.terramax.tour.RoutePlanner.PlannedStop;
import de.thi.terramax.tour.RoutePlanner.RouteStop;
import java.util.List;
import org.junit.jupiter.api.Test;

class NearestNeighbourRoutePlannerTest {

    private static final Coordinates WELS = new Coordinates(48.16, 14.03);

    private final NearestNeighbourRoutePlanner planner = new NearestNeighbourRoutePlanner();

    private static List<Long> order(List<PlannedStop> route) {
        return route.stream().map(PlannedStop::customerId).toList();
    }

    @Test
    void alwaysGoesToNearestOpenStop() {
        // Auf einer Linie nach Osten: 1 liegt am weitesten weg, 3 am nächsten am Start
        List<RouteStop> stops = List.of(new RouteStop(1, 48.16, 15.03), new RouteStop(2, 48.16, 14.53),
                new RouteStop(3, 48.16, 14.13));

        List<PlannedStop> route = planner.plan(WELS, stops);

        assertEquals(List.of(3L, 2L, 1L), order(route));
        assertEquals(List.of(1, 2, 3), route.stream().map(PlannedStop::position).toList());
        assertNull(route.get(0).driveTime());
    }

    @Test
    void distancesAreFromPreviousStop() {
        List<PlannedStop> route = planner.plan(WELS, List.of(new RouteStop(1, 48.16, 14.13)));
        // 0,1 Grad Länge auf 48 Grad Breite sind etwa 7,4 km
        assertEquals(7.4, route.get(0).distanceKm(), 0.1);
    }

    @Test
    void withoutStartBeginsAtFirstStop() {
        List<RouteStop> stops = List.of(new RouteStop(1, 48.0, 14.0), new RouteStop(2, 48.0, 16.0),
                new RouteStop(3, 48.0, 14.5));

        List<PlannedStop> route = planner.plan(null, stops);

        assertEquals(List.of(1L, 3L, 2L), order(route));
        assertNull(route.get(0).distanceKm());
    }

    @Test
    void stopsWithoutCoordinatesComeLastInInputOrder() {
        List<RouteStop> stops = List.of(new RouteStop(1, null, null), new RouteStop(2, 48.2, 14.1),
                new RouteStop(3, 48.2, null));

        List<PlannedStop> route = planner.plan(WELS, stops);

        assertEquals(List.of(2L, 1L, 3L), order(route));
        assertNull(route.get(1).distanceKm());
    }

    @Test
    void equalDistanceKeepsInputOrder() {
        // Exakt gleich weit östlich und westlich vom Start (ohne Rundungsunterschiede)
        List<RouteStop> stops = List.of(new RouteStop(5, 48.0, 14.5), new RouteStop(4, 48.0, 13.5));
        assertEquals(List.of(5L, 4L), order(planner.plan(new Coordinates(48.0, 14.0), stops)));
    }

    @Test
    void emptyInputGivesEmptyRoute() {
        assertEquals(List.of(), planner.plan(WELS, List.of()));
    }
}
