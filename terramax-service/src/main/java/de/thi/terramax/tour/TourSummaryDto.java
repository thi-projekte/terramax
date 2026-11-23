package de.thi.terramax.tour;

import java.time.LocalDate;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Tour in Listen, ohne Kunden.
 */
@Schema(name = "TourSummary", description = "Besuchstour ohne Kundendetails")
public record TourSummaryDto(@Schema(example = "1") Long id, @Schema(example = "2026-11-20") LocalDate tourDate,
        @Schema(enumeration = {
                "planned", "in_progress", "completed",
                "discarded", "aborted" }, example = "planned") String status,
        String processInstanceId, @Schema(description = "Anzahl Kunden", example = "5") int customerCount){

    public static TourSummaryDto from(Tour tour) {
        return new TourSummaryDto(tour.id, tour.tourDate, tour.status.getDbValue(), tour.processInstanceId,
                tour.stops.size());
    }
}
