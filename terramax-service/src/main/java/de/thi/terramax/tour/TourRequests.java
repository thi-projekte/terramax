package de.thi.terramax.tour;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request-Formate der Tour-Endpunkte.
 */
public final class TourRequests {

    /** Wie {@code tour_customer.skip_reason}. */
    static final int MAX_SKIP_REASON_LENGTH = 1000;
    /** Wie {@code tour.abort_reason}. */
    static final int MAX_ABORT_REASON_LENGTH = 1000;
    /** Wie {@code tour.process_instance_id}. */
    static final int MAX_PROCESS_INSTANCE_ID_LENGTH = 64;

    private TourRequests() {
    }

    @Schema(name = "CreateTourRequest", description = "Neue Tour. Mit processInstanceId idempotent: Gibt es zur "
            + "Prozessinstanz schon eine Tour, kommt diese zurück (200) und der Rest des Requests wird ignoriert.")
    public record Create(@Schema(required = true, example = "2026-11-24") LocalDate tourDate,
            @Schema(description = "Kunden des Händlers in Besuchsreihenfolge (aus GET /tours/plan: visits[].customer.id)", example = "[3, 1, 2]") List<Long> customerIds,
            @Schema(description = "Weitere Mitarbeiter des Händlers. Ein angemeldeter Nutzer wird immer eingetragen, "
                    + "wenn es für ihn einen Mitarbeiter-Eintrag gibt.", example = "[2]") List<Long> employeeIds,
            @Schema(description = "Prozessinstanz in CIB Seven (execution.getProcessInstanceId()), höchstens 64 Zeichen", example = "8f2c1a7e-1b2c-11f0-9d5e-0242ac120002") String processInstanceId,
            @Schema(description = "Nur für das Service-Konto (Pflicht, es hat kein dealer_id im Token). Nutzer lassen "
                    + "das Feld weg; ein abweichender Wert ergibt 403.", example = "D-100") String dealerId) {
    }

    @Schema(name = "PatchTourRequest", description = "Tour teilweise ändern. Nicht gesetzte Felder bleiben unverändert. "
            + "Den Stand einzelner Halte ändern nur die Endpunkte /tours/{id}/stops/{customerId}/start|skip|complete.")
    public record Patch(
            @Schema(enumeration = {
                    "planned", "in_progress", "completed",
                    "discarded" }, example = "completed") String status,
            @Schema(description = "Neue Reihenfolge: alle Kunden der Tour, jeder genau einmal", example = "[2, 3, 1]") List<Long> customerOrder){
    }

    @Schema(name = "StopTimeRequest", description = "Optional. Ohne Body oder ohne at gilt die aktuelle Zeit des Servers.")
    public record StopTime(
            @Schema(description = "Zeitpunkt, z. B. aus besuchBegonnenAm bzw. besuchAbgeschlossenAm", example = "2026-11-24T09:15:00+01:00") OffsetDateTime at) {
    }

    @Schema(name = "AbortTourRequest", description = "Optional. Ohne Body oder ohne reason wird die Tour ohne Grund abgebrochen.")
    public record Abort(
            @Schema(description = "Grund des Abbruchs, höchstens 1000 Zeichen", example = "Unwetter") String reason) {
    }

    @Schema(name = "SkipStopRequest", description = "Besuch überspringen, Grund ist Pflicht (Akzeptanzkriterium #29)")
    public record Skip(
            @Schema(required = true, description = "Grund fürs Überspringen (ueberspringenGrund), höchstens 1000 Zeichen", example = "Betrieb nicht erreichbar") String reason,
            @Schema(description = "Zeitpunkt, ohne Angabe die aktuelle Zeit", example = "2026-11-24T10:30:00+01:00") OffsetDateTime at) {
    }
}
