package de.thi.terramax.tour;

import java.time.Duration;
import java.util.List;

/**
 * Bringt priorisierte Kunden in eine Fahrreihenfolge und schätzt die Fahrzeiten. Platzhalter:
 * {@link NearestNeighbourRoutePlanner}.
 * <p>
 * TODO(#64-meeting): Mapbox Optimization API
 */
public interface RoutePlanner {

    /**
     * @param start
     *            Startpunkt, z. B. der Händler; {@code null}, wenn unbekannt
     *
     * @return alle Stopps in Fahrreihenfolge (Position ab 1)
     */
    List<PlannedStop> plan(Coordinates start, List<RouteStop> stops);

    record Coordinates(double latitude, double longitude) {
    }

    /** Ein Kunde mit Koordinaten; ohne Koordinaten ({@code null}) kommt er ans Ende. */
    record RouteStop(long customerId, Double latitude, Double longitude) {

        boolean hasCoordinates() {
            return latitude != null && longitude != null;
        }
    }

    /**
     * @param distanceKm
     *            Luftlinie zum vorherigen Stopp (beim ersten zum Start), {@code null} wenn nicht berechenbar
     * @param driveTime
     *            Fahrzeit zum vorherigen Stopp, {@code null} wenn unbekannt
     */
    record PlannedStop(long customerId, int position, Double distanceKm, Duration driveTime) {
    }
}
