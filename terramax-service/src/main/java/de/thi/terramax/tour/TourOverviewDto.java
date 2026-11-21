package de.thi.terramax.tour;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Tourübersicht ({@code GET /tours/{id}/overview}). Ersetzt die Prozessvariable {@code touruebersicht} der
 * process-engine, deshalb mit denselben Feldnamen ({@code tourDatum}, {@code besuche}, {@code anzahl…}). Statt des
 * Protokollinhalts stehen die IDs der Protokolle drin ({@code GET /protocols/{id}}).
 */
@Schema(name = "Touruebersicht", description = "Tourübersicht, ersetzt die Prozessvariable touruebersicht")
public record TourOverviewDto(@Schema(example = "1") long tourId,
        @Schema(example = "8f2c1a7e-1b2c-11f0-9d5e-0242ac120002") String processInstanceId,
        @Schema(example = "2026-11-20") LocalDate tourDatum,
        @Schema(enumeration = {
                "planned", "in_progress", "completed",
                "discarded", "aborted" }, example = "completed") String tourStatus,
        @Schema(description = "Grund des Abbruchs, nur bei aborted", example = "Unwetter") String abbruchGrund,
        @Schema(description = "Zeitpunkt des Abbruchs, nur bei aborted") OffsetDateTime abgebrochenAm,
        @Schema(description = "Alle Halte in Besuchsreihenfolge") List<Besuch> besuche,
        @Schema(example = "3") int anzahlDokumentiert, @Schema(example = "1") int anzahlUebersprungen,
        @Schema(example = "1") int anzahlOffen){

    @Schema(name = "TouruebersichtBesuch", description = "Ein Halt in der Tourübersicht")
    public record Besuch(@Schema(example = "1") int position, @Schema(example = "1") long kundeId,
            @Schema(example = "MOCK-001") String externalId, @Schema(example = "Testbetrieb 01 (fiktiv)") String name,
            @Schema(description = "Region des Kunden", example = "Marchfeld") String ort,
            @Schema(enumeration = {
                    "OFFEN", "DOKUMENTIERT", "UEBERSPRUNGEN" }, example = "DOKUMENTIERT") String status,
            @Schema(description = "Grund fürs Überspringen, nur bei UEBERSPRUNGEN", example = "Betrieb nicht erreichbar") String grund,
            OffsetDateTime besuchBegonnenAm, OffsetDateTime besuchAbgeschlossenAm,
            @Schema(description = "Besuchsprotokolle zu diesem Halt, älteste zuerst", example = "[7]") List<Long> protokollIds){
    }

    /**
     * @param protocolIds
     *            Protokoll-IDs je Kunden-ID
     */
    public static TourOverviewDto from(Tour tour, Map<Long, List<Long>> protocolIds) {
        List<Besuch> besuche = tour.stops.stream()
                .map(s -> new Besuch(s.position, s.customer.id, s.customer.externalId, s.customer.name,
                        s.customer.region, s.status.name(), s.skipReason, s.startedAt, s.finishedAt,
                        protocolIds.getOrDefault(s.customer.id, List.of())))
                .toList();
        return new TourOverviewDto(tour.id, tour.processInstanceId, tour.tourDate, tour.status.getDbValue(),
                tour.abortReason, tour.abortedAt, besuche,
                TourProcessStatusDto.count(tour.stops, StopStatus.DOKUMENTIERT),
                TourProcessStatusDto.count(tour.stops, StopStatus.UEBERSPRUNGEN),
                TourProcessStatusDto.count(tour.stops, StopStatus.OFFEN));
    }
}
