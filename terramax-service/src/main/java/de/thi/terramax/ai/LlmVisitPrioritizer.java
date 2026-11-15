package de.thi.terramax.ai;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.service.output.OutputParsingException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Priorisierung per OpenAI. Noch nicht Standard, Auswahl über {@code terramax.prioritizer.mode=llm}.
 * <p>
 * Datensparsamkeit: An das LLM gehen nur Kunden-ID, Region, Segment und das Datum des letzten Besuchs, keine Namen,
 * Kontaktdaten oder Koordinaten. Die Antwort wird geprüft: unbekannte und doppelte IDs fallen weg, höchstens
 * {@code maxCustomers} Kunden.
 * <p>
 * TODO(#64-meeting): Prompt und Kriterien mit dem Team abstimmen, dann als Standard aktivieren
 */
@ApplicationScoped
@Typed(LlmVisitPrioritizer.class)
public class LlmVisitPrioritizer implements VisitPrioritizer {

    private final VisitPrioritizationAssistant assistant;

    @Inject
    public LlmVisitPrioritizer(VisitPrioritizationAssistant assistant) {
        this.assistant = assistant;
    }

    @Override
    public List<PrioritizedCustomer> prioritize(List<Candidate> candidates, PlanningConstraints constraints) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        VisitPrioritization answer;
        try {
            answer = assistant.prioritize(describe(candidates), constraints.date(), constraints.maxCustomers());
        } catch (RuntimeException e) {
            throw translate(e);
        }
        if (answer == null || answer.customers() == null) {
            throw new PrioritizationException("Leere Antwort des LLM");
        }

        Map<Long, Candidate> known = candidates.stream()
                .collect(Collectors.toMap(Candidate::customerId, Function.identity(), (a, b) -> a));
        Set<Long> seen = new HashSet<>();
        List<PrioritizedCustomer> result = new ArrayList<>();
        for (VisitPrioritization.Entry entry : answer.customers()) {
            if (result.size() == constraints.maxCustomers()) {
                break;
            }
            if (entry != null && known.containsKey(entry.customerId()) && seen.add(entry.customerId())) {
                String reason = entry.reason() == null ? "" : entry.reason().strip();
                result.add(new PrioritizedCustomer(entry.customerId(), result.size() + 1, reason));
            }
        }
        return result;
    }

    /** Eine Zeile pro Kunde: {@code id | region | segment | letzter Besuch}. */
    static String describe(List<Candidate> candidates) {
        return candidates.stream()
                .map(c -> c.customerId() + " | " + orUnknown(c.region()) + " | " + orUnknown(c.segment()) + " | "
                        + (c.lastVisit() == null ? "nie" : c.lastVisit().toString()))
                .collect(Collectors.joining("\n"));
    }

    // Die Ursache wird bewusst nicht weitergegeben: Fehlertexte des Anbieters können Teile der Anfrage enthalten
    static PrioritizationException translate(RuntimeException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpException http) {
                return new PrioritizationException("LLM antwortet mit Status " + http.statusCode());
            }
            if (t instanceof OutputParsingException) {
                return new PrioritizationException("Antwort des LLM passt nicht zum Schema");
            }
        }
        return new PrioritizationException("LLM nicht erreichbar (" + e.getClass().getSimpleName() + ")");
    }

    private static String orUnknown(String value) {
        return value == null || value.isBlank() ? "unbekannt" : value.replace('\n', ' ').replace('|', '/');
    }
}
