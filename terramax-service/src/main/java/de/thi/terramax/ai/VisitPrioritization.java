package de.thi.terramax.ai;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

/**
 * Strukturierte Antwort des LLM. Die Beschreibungen gehen als JSON-Schema an OpenAI.
 */
public record VisitPrioritization(
        @Description("Ausgewählte Kunden, wichtigster zuerst. Nur IDs aus der Eingabe, jede höchstens einmal") List<Entry> customers) {

    public record Entry(@Description("ID des Kunden aus der Eingabe") long customerId,
            @Description("Begründung in einem kurzen Satz auf Deutsch") String reason) {
    }
}
