package de.thi.terramax.tour;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Tour mit ihren Kunden in Reihenfolge.
 */
@Schema(name = "Tour", description = "Besuchstour mit Kunden in Reihenfolge")
public record TourDto(@Schema(example = "1") Long id, @Schema(example = "2026-11-20") LocalDate tourDate,
        @Schema(enumeration = {
                "planned", "in_progress", "completed",
                "discarded" }, example = "planned") String status,
        @Schema(description = "Prozessinstanz in CIB Seven, null solange keine gestartet ist") String processInstanceId,
        OffsetDateTime createdAt,
        @Schema(description = "IDs der Mitarbeiter, die die Tour fahren") List<Long> employeeIds,
        @Schema(description = "Kunden in Besuchsreihenfolge") List<Stop> customers){

    @Schema(name = "TourStop", description = "Kunde auf einer Tour")
    public record Stop(@Schema(example = "1") Long customerId, @Schema(example = "CRM-4711") String externalId,
            @Schema(example = "Hof Huber") String name,
            @Schema(description = "Position, ab 1", example = "1") int position,
            boolean visited, Double latitude, Double longitude) {
    }

    public static TourDto from(Tour tour) {
        List<Stop> stops = tour.stops.stream().map(s -> new Stop(s.customer.id, s.customer.externalId, s.customer.name,
                s.position, s.visited, s.customer.latitude, s.customer.longitude)).toList();
        List<Long> employeeIds = tour.employees.stream().map(e -> e.id).sorted().toList();
        return new TourDto(tour.id, tour.tourDate, tour.status.getDbValue(), tour.processInstanceId, tour.createdAt,
                employeeIds, stops);
    }
}
