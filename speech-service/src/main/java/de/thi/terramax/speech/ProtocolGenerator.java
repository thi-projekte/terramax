package de.thi.terramax.speech;

import java.time.LocalDate;

/**
 * Erstellt aus einem Transkript ein Besuchsprotokoll. Die Resource kennt nur dieses Interface, damit Tests ohne LLM
 * auskommen.
 */
public interface ProtocolGenerator {

    /**
     * @param today
     *            Bezugsdatum für relative Angaben wie "nächste Woche"
     *
     * @throws ProtocolGenerationException
     *             wenn das LLM nicht erreichbar ist oder unbrauchbar antwortet
     */
    VisitProtocol generate(String transcript, LocalDate today);
}
