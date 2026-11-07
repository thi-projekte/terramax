package de.thi.terramax.tour;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Erzeugt den Besuchsplan nach der MVP-Regel aus dem BPMN-Modell "Besuchstour" (Service Task "Besuchsplan erzeugen"):
 * <ol>
 * <li><b>Auswahl:</b> die Kunden mit dem am längsten zurückliegenden Besuch; noch nie besuchte Kunden zuerst. Kunden,
 * die am Tourtag oder danach schon besucht wurden, fallen heraus.</li>
 * <li><b>Reihenfolge:</b> nach Luftlinie ab Standort – immer der nächstgelegene noch offene Kunde (Nearest-Neighbour).
 * Keine Routenoptimierung.</li>
 * </ol>
 * Die Klasse ist bewusst unabhängig von REST und BPMN, damit sie später auch vom Delegate {@code besuchsplanErzeugen}
 * aufgerufen werden kann.
 */
@ApplicationScoped
public class VisitPlanner {

    private final CustomerRepository customers;
    private final GeoPoint start;

    @Inject
    public VisitPlanner(CustomerRepository customers,
            @ConfigProperty(name = "tour.start.latitude") double startLatitude,
            @ConfigProperty(name = "tour.start.longitude") double startLongitude) {
        this.customers = customers;
        this.start = new GeoPoint(startLatitude, startLongitude);
    }

    /**
     * @param date
     *            Tag der Tour
     * @param maxVisits
     *            höchstens so viele Kunden kommen in den Plan
     *
     * @return Plan mit höchstens {@code maxVisits} Besuchen; kann weniger als zwei enthalten, wenn nicht genug Kunden
     *         in Frage kommen (im BPMN-Modell endet die Tour dann mit "Keine Tour möglich")
     */
    public VisitPlan plan(LocalDate date, int maxVisits) {
        List<Customer> selected = customers.findAll().stream()
                .filter(c -> c.lastVisit() == null || c.lastVisit().isBefore(date))
                .sorted(Comparator.comparing(Customer::lastVisit, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Customer::id))
                .limit(maxVisits)
                .toList();

        List<Customer> open = new ArrayList<>(selected);
        List<VisitPlan.PlannedVisit> visits = new ArrayList<>();
        GeoPoint current = start;
        double total = 0;
        while (!open.isEmpty()) {
            GeoPoint from = current;
            Customer next = open.stream()
                    .min(Comparator.comparingDouble((Customer c) -> from.distanceKmTo(c.position()))
                            .thenComparing(Customer::id))
                    .orElseThrow();
            double distance = from.distanceKmTo(next.position());
            visits.add(new VisitPlan.PlannedVisit(visits.size() + 1, next, round(distance)));
            total += distance;
            current = next.position();
            open.remove(next);
        }
        return new VisitPlan(date, start, List.copyOf(visits), round(total));
    }

    private static double round(double km) {
        return Math.round(km * 10) / 10.0;
    }
}
