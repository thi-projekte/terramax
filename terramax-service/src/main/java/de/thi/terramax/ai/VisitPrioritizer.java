package de.thi.terramax.ai;

import java.time.LocalDate;
import java.util.List;

/**
 * Wählt aus den Kunden eines Händlers die aus, die als Nächstes besucht werden sollten, mit Begründung pro Kunde.
 * Implementierungen: {@link LongestUnvisitedPrioritizer} (Platzhalter, Standard) und {@link LlmVisitPrioritizer}
 * (OpenAI). Auswahl über {@code terramax.prioritizer.mode=stub|llm}, siehe {@link VisitPrioritizerProducer}.
 * <p>
 * TODO(#64-meeting): Kriterien der Priorisierung (Umsatz, offene Angebote, Follow-ups, Saison) mit dem Team festlegen
 */
public interface VisitPrioritizer {

    /**
     * @return höchstens {@code constraints.maxCustomers()} Kunden, wichtigster zuerst (Rang ab 1)
     *
     * @throws PrioritizationException
     *             wenn die Priorisierung nicht möglich ist (z. B. LLM nicht erreichbar)
     */
    List<PrioritizedCustomer> prioritize(List<Candidate> candidates, PlanningConstraints constraints);

    /**
     * Ein Kunde, wie ihn die Priorisierung sieht. Bewusst ohne Name und Kontaktdaten: Diese Daten gehen beim LLM an
     * einen externen Anbieter.
     */
    record Candidate(long customerId, String region, String segment, LocalDate lastVisit) {
    }

    /** Rahmen der Planung: Datum der Tour und wie viele Kunden höchstens. */
    record PlanningConstraints(LocalDate date, int maxCustomers) {

        public PlanningConstraints {
            if (date == null) {
                throw new IllegalArgumentException("Datum fehlt");
            }
            if (maxCustomers < 1) {
                throw new IllegalArgumentException("maxCustomers muss mindestens 1 sein");
            }
        }
    }

    /** Ergebnis pro Kunde. */
    record PrioritizedCustomer(long customerId, int rank, String reason) {
    }
}
