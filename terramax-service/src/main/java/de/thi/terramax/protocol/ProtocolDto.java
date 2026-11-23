package de.thi.terramax.protocol;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Gespeichertes Besuchsprotokoll.
 */
@Schema(name = "VisitorProtocol", description = "Besuchsprotokoll")
public record ProtocolDto(@Schema(example = "1") Long id, @Schema(example = "1") Long tourId,
        @Schema(example = "1") Long customerId, @Schema(example = "Herr Huber (Betriebsleiter)") String contactPerson,
        @Schema(example = "[\"Neuer Traktor\"]") List<String> topics,
        @Schema(example = "Interesse an einem 6R 250") String outcome,
        @Schema(example = "[\"Angebot schicken\"]") List<String> nextSteps,
        @Schema(example = "2026-11-27") LocalDate followUpDate,
        @Schema(description = "Transkript der Sprachnotiz, falls vorhanden") String transcript,
        @Schema(description = "Claim sub des Erstellers") String createdBy, OffsetDateTime createdAt) {

    public static ProtocolDto from(VisitorProtocol p) {
        return new ProtocolDto(p.id, p.tour.id, p.customer.id, p.contactPerson, lines(p.topics), p.outcome,
                lines(p.nextSteps), p.followUpDate, p.transcript, p.createdBy, p.createdAt);
    }

    /** Ein Stichpunkt pro Zeile, wie in der Datenbank gespeichert. */
    static List<String> lines(String text) {
        return text == null || text.isBlank() ? List.of()
                : Arrays.stream(text.split("\n")).filter(s -> !s.isBlank()).toList();
    }
}
