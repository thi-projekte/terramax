package de.thi.terramax.speech;

import dev.langchain4j.model.output.structured.Description;

import java.time.LocalDate;
import java.util.List;

/**
 * Besuchsprotokoll mit festen Feldern. Die AI füllt es aus dem Transkript vor, der Sales Professional prüft und
 * bearbeitet es vor dem Speichern. Die Beschreibungen gehen als JSON-Schema an das LLM.
 */
public record VisitProtocol(
        @Description("Name und ggf. Funktion der Gesprächspartner beim Kunden, null wenn nicht genannt") String contactPerson,
        @Description("Besprochene Themen als kurze Stichpunkte, leer wenn keine genannt") List<String> topics,
        @Description("Ergebnis des Gesprächs in ein bis drei Sätzen, null wenn nicht genannt") String outcome,
        @Description("Vereinbarte nächste Schritte als kurze Stichpunkte, leer wenn keine genannt") List<String> nextSteps,
        @Description("Datum des vereinbarten Follow-ups im Format JJJJ-MM-TT, null wenn keins genannt") LocalDate followUpDate) {
}
