package de.thi.terramax.tour;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Tour mit ihren Halten in Reihenfolge.
 */
@Schema(name = "Tour", description = "Besuchstour mit Halten in Reihenfolge")
public record TourDto(@Schema(example = "1") Long id,
        @Schema(example = "D-100") String dealerId, @Schema(example = "2026-11-20") LocalDate tourDate,
        @Schema(enumeration = {
                "planned", "in_progress", "completed",
                "discarded", "aborted" }, example = "planned") String status,
        @Schema(description = "Prozessinstanz in CIB Seven, null bei Touren ohne Prozess", example = "8f2c1a7e-1b2c-11f0-9d5e-0242ac120002") String processInstanceId,
        OffsetDateTime createdAt,
        @Schema(description = "Grund des Abbruchs, nur bei aborted (optional)", example = "Unwetter") String abortReason,
        @Schema(description = "Zeitpunkt des Abbruchs, nur bei aborted") OffsetDateTime abortedAt,
        @Schema(description = "IDs der Mitarbeiter, die die Tour fahren", example = "[1]") List<Long> employeeIds,
        @Schema(description = "Halte in Besuchsreihenfolge") List<Stop> customers){

    @Schema(name = "TourStop", description = "Halt: Kunde auf einer Tour")
    public record Stop(@Schema(example = "1") Long customerId, @Schema(example = "MOCK-001") String externalId,
            @Schema(example = "Testbetrieb 01 (fiktiv)") String name,
            @Schema(description = "Position, ab 1", example = "1") int position,
            @Schema(enumeration = {
                    "OFFEN", "DOKUMENTIERT",
                    "UEBERSPRUNGEN" }, example = "OFFEN") String status,
            @Schema(description = "Grund fürs Überspringen, nur bei UEBERSPRUNGEN", example = "Betrieb nicht erreichbar") String skipReason,
            @Schema(description = "Besuch begonnen, null wenn nicht begonnen") OffsetDateTime startedAt,
            @Schema(description = "Halt erledigt (dokumentiert oder übersprungen)") OffsetDateTime finishedAt,
            @Schema(example = "48.235") Double latitude, @Schema(example = "16.65") Double longitude){

        static Stop from(TourCustomer s) {
            return new Stop(s.customer.id, s.customer.externalId, s.customer.name, s.position, s.status.name(),
                    s.skipReason, s.startedAt, s.finishedAt, s.customer.latitude, s.customer.longitude);
        }
    }

    public static TourDto from(Tour tour) {
        List<Stop> stops = tour.stops.stream().map(Stop::from).toList();
        List<Long> employeeIds = tour.employees.stream().map(e -> e.id).sorted().toList();
        return new TourDto(tour.id, tour.dealer.id, tour.tourDate, tour.status.getDbValue(),
                tour.processInstanceId, tour.createdAt, tour.abortReason, tour.abortedAt, employeeIds, stops);
    }
}
