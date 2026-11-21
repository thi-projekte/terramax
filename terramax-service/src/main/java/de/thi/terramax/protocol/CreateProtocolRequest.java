package de.thi.terramax.protocol;

import de.thi.terramax.common.ApiErrors;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Neues Besuchsprotokoll. Die Felder entsprechen {@code VisitProtocol} im speech-service, dazu der Kunde und optional
 * das Transkript.
 */
@Schema(name = "CreateProtocolRequest", description = "Besuchsprotokoll speichern (Felder wie VisitProtocol im speech-service)")
public record CreateProtocolRequest(@Schema(required = true, example = "1") Long customerId,
        @Schema(example = "Herr Huber (Betriebsleiter)") String contactPerson,
        @Schema(example = "[\"Neuer Traktor\"]") List<String> topics,
        @Schema(example = "Interesse an einem 6R 250") String outcome,
        @Schema(example = "[\"Angebot schicken\"]") List<String> nextSteps,
        @Schema(example = "2026-11-27") LocalDate followUpDate,
        @Schema(description = "Transkript der Sprachnotiz, optional") String transcript) {

    /** Großzügig über dem Limit des speech-service (5 000 Zeichen). */
    static final int MAX_TRANSCRIPT_LENGTH = 20_000;

    void validate() {
        if (customerId == null) {
            throw ApiErrors.badRequest("Die customerId fehlt.");
        }
        if (transcript != null && transcript.length() > MAX_TRANSCRIPT_LENGTH) {
            throw ApiErrors.badRequest("Das Transkript ist zu lang.");
        }
    }

    /** Prüfung für PUT und PATCH: customerId ist dort optional. */
    void validateUpdate() {
        if (transcript != null && transcript.length() > MAX_TRANSCRIPT_LENGTH) {
            throw ApiErrors.badRequest("Das Transkript ist zu lang.");
        }
    }

    /** PATCH: nur gesetzte Felder übernehmen; eine leere Liste oder ein leerer Text löscht den Wert. */
    void mergeInto(VisitorProtocol protocol) {
        if (contactPerson != null) {
            protocol.contactPerson = blankToNull(contactPerson);
        }
        if (topics != null) {
            protocol.topics = joinLines(topics);
        }
        if (outcome != null) {
            protocol.outcome = blankToNull(outcome);
        }
        if (nextSteps != null) {
            protocol.nextSteps = joinLines(nextSteps);
        }
        if (followUpDate != null) {
            protocol.followUpDate = followUpDate;
        }
        if (transcript != null) {
            protocol.transcript = blankToNull(transcript);
        }
    }

    /** POST und PUT: alle Inhaltsfelder setzen, fehlende werden leer. */
    void applyTo(VisitorProtocol protocol) {
        protocol.contactPerson = blankToNull(contactPerson);
        protocol.topics = joinLines(topics);
        protocol.outcome = blankToNull(outcome);
        protocol.nextSteps = joinLines(nextSteps);
        protocol.followUpDate = followUpDate;
        protocol.transcript = blankToNull(transcript);
    }

    /** Ein Stichpunkt pro Zeile; Zeilenumbrüche innerhalb eines Stichpunkts werden zu Leerzeichen. */
    static String joinLines(List<String> items) {
        if (items == null) {
            return null;
        }
        String joined = items.stream().filter(s -> s != null && !s.isBlank())
                .map(s -> s.replaceAll("\\s*\\R\\s*", " ").strip()).collect(Collectors.joining("\n"));
        return joined.isEmpty() ? null : joined;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
