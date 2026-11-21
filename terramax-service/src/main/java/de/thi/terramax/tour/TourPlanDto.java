package de.thi.terramax.tour;

import de.thi.terramax.customer.Customer;
import de.thi.terramax.tour.TourPlanner.ProposedStop;
import de.thi.terramax.tour.TourPlanner.TourPlanProposal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Antwort von {@code GET /tours/plan}, exakt im Format von {@code TourPlan} der process-engine
 * ({@code process-engine/src/main/java/de/thi/terramax/process/tour/TourPlan.java}) und damit des bisherigen
 * tour-service. Die Engine muss nur {@code TOUR_SERVICE_URL} auf den terramax-service umstellen.
 * <p>
 * {@code customer.id} ist die Datenbank-ID des Kunden als String; genau diese IDs gehen beim Anlegen der Tour als
 * {@code customerIds} an {@code POST /tours}. Entfernungen sind Luftlinie in km, auf eine Nachkommastelle gerundet.
 */
@Schema(name = "TourPlan", description = "Besuchsplan, Format wie TourPlan in der process-engine (bisher tour-service)")
public record TourPlanDto(@Schema(example = "2026-11-24") LocalDate date,
        @Schema(description = "Besuche in Fahrreihenfolge") List<Visit> visits,
        @Schema(description = "Summe der Luftlinien vom Händler über alle Besuche, ohne Rückweg", example = "42.7") double totalDistanceKm) {

    @Schema(name = "TourPlanVisit", description = "Ein Besuch im Plan")
    public record Visit(@Schema(description = "Reihenfolge ab 1", example = "1") int position, PlanCustomer customer,
            @Schema(description = "Luftlinie vom vorherigen Halt, beim ersten vom Händler", example = "12.4") double distanceFromPreviousKm) {
    }

    @Schema(name = "TourPlanCustomer", description = "Kunde im Plan")
    public record PlanCustomer(@Schema(description = "Datenbank-ID des Kunden als String", example = "3") String id,
            @Schema(example = "Testbetrieb 03 (fiktiv)") String name,
            @Schema(description = "Region des Kunden", example = "Weinviertel") String location, Position position,
            @Schema(description = "Letzter Besuch (JJJJ-MM-TT), null wenn nie besucht", example = "2026-09-02") LocalDate lastVisit) {
    }

    @Schema(name = "TourPlanPosition", description = "Koordinaten (WGS84)")
    public record Position(@Schema(example = "48.235") double latitude, @Schema(example = "16.65") double longitude) {
    }

    /**
     * @param customers
     *            alle Kunden des Vorschlags nach ID
     */
    public static TourPlanDto from(TourPlanProposal proposal, Map<Long, Customer> customers) {
        double total = 0;
        List<Visit> visits = new ArrayList<>(proposal.stops().size());
        for (ProposedStop stop : proposal.stops()) {
            Customer c = customers.get(stop.customerId());
            double distance = stop.distanceKm() == null ? 0 : stop.distanceKm();
            total += distance;
            visits.add(new Visit(stop.position(),
                    new PlanCustomer(String.valueOf(c.id), c.name, c.region, new Position(c.latitude, c.longitude),
                            stop.lastVisit()),
                    round(distance)));
        }
        return new TourPlanDto(proposal.date(), List.copyOf(visits), round(total));
    }

    /** Wie im tour-service: eine Nachkommastelle. */
    static double round(double km) {
        return Math.round(km * 10) / 10.0;
    }
}
