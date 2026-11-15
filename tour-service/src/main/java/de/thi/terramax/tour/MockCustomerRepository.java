package de.thi.terramax.tour;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.List;

/**
 * Feste Mock-Kunden für das MVP (#29: "einfache Regel + Mock-Kunden"). Alle Betriebe sind frei erfunden und liegen in
 * Oberösterreich rund um den Standort aus {@code tour.start.*}.
 */
@ApplicationScoped
public class MockCustomerRepository implements CustomerRepository {

    private static final List<Customer> CUSTOMERS = List.of(
            new Customer("K-1001", "Hofgut Aigner", "Gunskirchen", new GeoPoint(48.1333, 13.9500),
                    LocalDate.of(2026, 3, 12)),
            new Customer("K-1002", "Biohof Leitner", "Eferding", new GeoPoint(48.3086, 14.0219),
                    LocalDate.of(2026, 9, 2)),
            new Customer("K-1003", "Ackerbau Huber GesbR", "Grieskirchen", new GeoPoint(48.2347, 13.8306),
                    null),
            new Customer("K-1004", "Landwirtschaft Moser", "Sattledt", new GeoPoint(48.0742, 14.0544),
                    LocalDate.of(2026, 5, 20)),
            new Customer("K-1005", "Gutshof Pichler", "Marchtrenk", new GeoPoint(48.1914, 14.1117),
                    LocalDate.of(2026, 10, 15)),
            new Customer("K-1006", "Agrar Wimmer KG", "Lambach", new GeoPoint(48.0925, 13.8753),
                    LocalDate.of(2026, 1, 28)),
            new Customer("K-1007", "Hof Brandstätter", "Kremsmünster", new GeoPoint(48.0528, 14.1297),
                    null),
            new Customer("K-1008", "Feldbau Schwarz", "Wallern an der Trattnach", new GeoPoint(48.2306, 13.9442),
                    LocalDate.of(2026, 7, 7)));

    @Override
    public List<Customer> findAll() {
        return CUSTOMERS;
    }
}
