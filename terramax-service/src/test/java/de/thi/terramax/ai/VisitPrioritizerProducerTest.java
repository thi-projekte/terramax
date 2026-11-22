package de.thi.terramax.ai;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VisitPrioritizerProducerTest {

    private final LongestUnvisitedPrioritizer stub = new LongestUnvisitedPrioritizer();
    private final LlmVisitPrioritizer llm = new LlmVisitPrioritizer((c, d, m) -> null);

    @Test
    void selectsByMode() {
        assertSame(stub, VisitPrioritizerProducer.select("stub", () -> stub, () -> llm));
        assertSame(llm, VisitPrioritizerProducer.select(" llm ", () -> stub, () -> llm));
    }

    @Test
    void unknownModeIsRejected() {
        assertThrows(IllegalStateException.class, () -> VisitPrioritizerProducer.select("openai", () -> stub,
                () -> llm));
    }
}
