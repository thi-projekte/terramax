package de.thi.terramax.tour;

import java.time.LocalDate;

/**
 * Kunde (landwirtschaftlicher Betrieb), den ein Sales Professional besuchen kann.
 *
 * @param lastVisit
 *            Datum des letzten Besuchs; {@code null}, wenn der Kunde noch nie besucht wurde
 */
public record Customer(String id, String name, String location, GeoPoint position, LocalDate lastVisit) {
}
