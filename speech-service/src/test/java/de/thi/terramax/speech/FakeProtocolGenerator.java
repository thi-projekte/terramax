package de.thi.terramax.speech;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.LocalDate;
import java.util.List;

/**
 * Ersetzt das LLM in den Tests. Merkt sich den letzten Aufruf und kann Fehler simulieren. Zugriff nur über Methoden,
 * weil Tests einen Proxy der Bean injiziert bekommen.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class FakeProtocolGenerator implements ProtocolGenerator {

    private ProtocolGenerationException.Reason failWith;
    private String lastTranscript;
    private LocalDate lastToday;

    void reset() {
        failWith = null;
        lastTranscript = null;
        lastToday = null;
    }

    void failWith(ProtocolGenerationException.Reason reason) {
        failWith = reason;
    }

    String lastTranscript() {
        return lastTranscript;
    }

    LocalDate lastToday() {
        return lastToday;
    }

    @Override
    public VisitProtocol generate(String transcript, LocalDate today) {
        lastTranscript = transcript;
        lastToday = today;
        if (failWith != null) {
            throw new ProtocolGenerationException(failWith, "simuliert");
        }
        return new VisitProtocol("Herr Maier (Betriebsleiter)", List.of("8R 410", "Finanzierung"),
                "Interesse an einer Vorführung.", List.of("Angebot schicken"), LocalDate.of(2026, 11, 28));
    }
}
