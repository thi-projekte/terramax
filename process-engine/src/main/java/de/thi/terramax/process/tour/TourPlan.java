package de.thi.terramax.process.tour;

import java.util.List;

/**
 * Antwort von {@code GET /tours/plan} des tour-service (nur die Felder, die die Engine braucht).
 */
public record TourPlan(String date, List<Visit> visits, double totalDistanceKm) {

    public record Visit(int position, Customer customer, double distanceFromPreviousKm) {
    }

    public record Customer(String id, String name, String location, Position position, String lastVisit) {
    }

    public record Position(double latitude, double longitude) {
    }
}
