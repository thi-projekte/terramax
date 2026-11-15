package de.thi.terramax.tour;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Platzhalter für die Routenplanung: Nearest Neighbour nach Luftlinie. Vom Start aus geht es immer zum nächstgelegenen
 * noch nicht besuchten Kunden. Ohne Start beginnt die Route beim ersten Kunden mit Koordinaten. Kunden ohne Koordinaten
 * kommen in der Eingabereihenfolge ans Ende. Fahrzeiten sind {@code null}.
 * <p>
 * TODO(#64-meeting): Mapbox Optimization API (echte Fahrzeiten und optimierte Reihenfolge)
 */
@ApplicationScoped
@DefaultBean
public class NearestNeighbourRoutePlanner implements RoutePlanner {

    private static final double EARTH_RADIUS_KM = 6371.0;

    @Override
    public List<PlannedStop> plan(Coordinates start, List<RouteStop> stops) {
        LinkedList<RouteStop> open = new LinkedList<>(stops.stream().filter(RouteStop::hasCoordinates).toList());
        List<PlannedStop> result = new ArrayList<>(stops.size());

        Coordinates current = start;
        while (!open.isEmpty()) {
            RouteStop next = current == null ? open.getFirst() : nearest(current, open);
            open.remove(next);
            Coordinates here = new Coordinates(next.latitude(), next.longitude());
            Double distance = current == null ? null : distanceKm(current, here);
            result.add(new PlannedStop(next.customerId(), result.size() + 1, distance, null));
            current = here;
        }
        for (RouteStop stop : stops) {
            if (!stop.hasCoordinates()) {
                result.add(new PlannedStop(stop.customerId(), result.size() + 1, null, null));
            }
        }
        return result;
    }

    /** Bei gleicher Entfernung gewinnt der frühere Eintrag, damit das Ergebnis stabil ist. */
    private static RouteStop nearest(Coordinates from, List<RouteStop> candidates) {
        RouteStop best = null;
        double bestDistance = Double.MAX_VALUE;
        for (RouteStop candidate : candidates) {
            double d = distanceKm(from, new Coordinates(candidate.latitude(), candidate.longitude()));
            if (d < bestDistance) {
                best = candidate;
                bestDistance = d;
            }
        }
        return best;
    }

    /** Luftlinie nach der Haversine-Formel. */
    static double distanceKm(Coordinates a, Coordinates b) {
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(a.latitude()))
                * Math.cos(Math.toRadians(b.latitude())) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
    }
}
