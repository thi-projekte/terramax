package de.thi.terramax.tour;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request-Formate der Tour-Endpunkte.
 */
public final class TourRequests {

    private TourRequests() {
    }

    @Schema(name = "CreateTourRequest", description = "Neue Tour")
    public record Create(@Schema(required = true, example = "2026-11-20") LocalDate tourDate,
            @Schema(description = "Kunden des eigenen Händlers in Besuchsreihenfolge", example = "[3, 1, 2]") List<Long> customerIds,
            @Schema(description = "Weitere Mitarbeiter des eigenen Händlers. Der angemeldete Nutzer wird immer eingetragen, "
                    + "wenn es für ihn einen Mitarbeiter-Eintrag gibt.", example = "[2]") List<Long> employeeIds) {
    }

    @Schema(name = "PatchTourRequest", description = "Tour teilweise ändern. Nicht gesetzte Felder bleiben unverändert.")
    public record Patch(
            @Schema(enumeration = {
                    "planned", "in_progress", "completed",
                    "discarded" }, example = "in_progress") String status,
            @Schema(description = "Neue Reihenfolge: alle Kunden der Tour, jeder genau einmal", example = "[2, 3, 1]") List<Long> customerOrder,
            @Schema(description = "Besuchsstatus je Kunde (Kunden-ID -> besucht)", example = "{\"3\": true}") Map<Long, Boolean> visited){
    }
}
