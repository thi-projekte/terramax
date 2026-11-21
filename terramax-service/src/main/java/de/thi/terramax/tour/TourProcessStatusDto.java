package de.thi.terramax.tour;

import java.util.Comparator;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Kompakter Stand einer Tour für Gateways der process-engine ({@code GET /tours/{id}/status}). Feldnamen auf Deutsch
 * wie die Prozessvariablen, damit sie sich direkt in Bedingungen verwenden lassen, z. B.
 * {@code ${tourStatus.anzahlHalte >= 2}} oder {@code ${tourStatus.alleErledigt}}.
 */
@Schema(name = "TourProcessStatus", description = "Kompakter Stand einer Tour für Gateways der process-engine")
public record TourProcessStatusDto(@Schema(example = "1") long tourId,
        @Schema(example = "8f2c1a7e-1b2c-11f0-9d5e-0242ac120002") String processInstanceId,
        @Schema(enumeration = {
                "planned", "in_progress", "completed",
                "discarded" }, example = "in_progress") String tourStatus,
        @Schema(description = "Alle Halte der Tour; Gateway \"Mindestens zwei Kunden?\": anzahlHalte >= 2", example = "5") int anzahlHalte,
        @Schema(description = "Halte im Status OFFEN (auch begonnene)", example = "3") int offeneHalte,
        @Schema(example = "1") int dokumentiert, @Schema(example = "1") int uebersprungen,
        @Schema(description = "true, wenn kein Halt mehr OFFEN ist (auch bei einer Tour ohne Halte)", example = "false") boolean alleErledigt,
        @Schema(description = "Erster offener Halt nach Position, null wenn alle erledigt") NaechsterHalt naechsterHalt){

    @Schema(name = "NaechsterHalt", description = "Nächster offener Halt")
    public record NaechsterHalt(@Schema(example = "3") long kundeId, @Schema(example = "3") int position,
            @Schema(example = "Testbetrieb 03 (fiktiv)") String name,
            @Schema(description = "Besuch schon begonnen (startedAt gesetzt)", example = "false") boolean begonnen) {
    }

    public static TourProcessStatusDto from(Tour tour) {
        List<TourCustomer> stops = tour.stops;
        int offen = count(stops, StopStatus.OFFEN);
        NaechsterHalt next = stops.stream().filter(s -> s.status == StopStatus.OFFEN)
                .min(Comparator.comparingInt(s -> s.position))
                .map(s -> new NaechsterHalt(s.customer.id, s.position, s.customer.name, s.startedAt != null))
                .orElse(null);
        return new TourProcessStatusDto(tour.id, tour.processInstanceId, tour.status.getDbValue(), stops.size(), offen,
                count(stops, StopStatus.DOKUMENTIERT), count(stops, StopStatus.UEBERSPRUNGEN), offen == 0, next);
    }

    static int count(List<TourCustomer> stops, StopStatus status) {
        return (int) stops.stream().filter(s -> s.status == status).count();
    }
}
