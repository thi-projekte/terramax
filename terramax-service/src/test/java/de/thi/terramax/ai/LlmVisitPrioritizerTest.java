package de.thi.terramax.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.thi.terramax.ai.VisitPrioritizer.Candidate;
import de.thi.terramax.ai.VisitPrioritizer.PlanningConstraints;
import de.thi.terramax.ai.VisitPrioritizer.PrioritizedCustomer;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.service.output.OutputParsingException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Prüft Aufbereitung und Fehlerübersetzung ohne Quarkus und ohne echtes LLM.
 */
class LlmVisitPrioritizerTest {

    private static final PlanningConstraints CONSTRAINTS = new PlanningConstraints(LocalDate.of(2026, 11, 20), 2);
    private static final List<Candidate> CANDIDATES = List.of(new Candidate(1, "Marchfeld", "Ackerbau", null),
            new Candidate(2, null, "Grünland", LocalDate.of(2026, 9, 1)), new Candidate(3, "Weinviertel", null, null));

    private static LlmVisitPrioritizer answering(VisitPrioritization answer) {
        return new LlmVisitPrioritizer((candidates, date, max) -> answer);
    }

    private static VisitPrioritization.Entry entry(long id, String reason) {
        return new VisitPrioritization.Entry(id, reason);
    }

    @Test
    void unknownAndDuplicateIdsAreDroppedAndResultIsLimited() {
        List<PrioritizedCustomer> result = answering(new VisitPrioritization(
                List.of(entry(99, "erfunden"), entry(3, " lange nicht da "), entry(3, "doppelt"), entry(1, null),
                        entry(2, "zu viel"))))
                                .prioritize(CANDIDATES, CONSTRAINTS);

        assertEquals(List.of(new PrioritizedCustomer(3, 1, "lange nicht da"), new PrioritizedCustomer(1, 2, "")),
                result);
    }

    @Test
    void emptyAnswerIsAnError() {
        assertThrows(PrioritizationException.class, () -> answering(null).prioritize(CANDIDATES, CONSTRAINTS));
        assertThrows(PrioritizationException.class,
                () -> answering(new VisitPrioritization(null)).prioritize(CANDIDATES, CONSTRAINTS));
    }

    @Test
    void noCandidatesDoNotCallTheLlm() {
        var prioritizer = new LlmVisitPrioritizer((candidates, date, max) -> {
            throw new AssertionError("LLM darf nicht aufgerufen werden");
        });
        assertEquals(List.of(), prioritizer.prioritize(List.of(), CONSTRAINTS));
    }

    @Test
    void describeSendsOnlyIdRegionSegmentAndLastVisit() {
        assertEquals("""
                1 | Marchfeld | Ackerbau | nie
                2 | unbekannt | Grünland | 2026-09-01
                3 | Weinviertel | unbekannt | nie""", LlmVisitPrioritizer.describe(CANDIDATES));
    }

    @Test
    void describeKeepsOneLinePerCustomer() {
        String text = LlmVisitPrioritizer.describe(List.of(new Candidate(1, "A\nB", "C|D", null)));
        assertEquals("1 | A B | C/D | nie", text);
    }

    @Test
    void errorsAreTranslatedWithoutProviderText() {
        var error = assertThrows(PrioritizationException.class, () -> new LlmVisitPrioritizer((c, d, m) -> {
            throw new RuntimeException(new HttpException(429, "Kunde 1 Marchfeld ..."));
        }).prioritize(CANDIDATES, CONSTRAINTS));
        assertEquals("LLM antwortet mit Status 429", error.getMessage());
        assertFalse(error.getMessage().contains("Marchfeld"));
        assertNull(error.getCause());

        assertEquals("Antwort des LLM passt nicht zum Schema",
                LlmVisitPrioritizer.translate(new OutputParsingException("kein JSON", null)).getMessage());
        assertEquals("LLM nicht erreichbar (IllegalStateException)",
                LlmVisitPrioritizer.translate(new IllegalStateException("timeout")).getMessage());
    }
}
