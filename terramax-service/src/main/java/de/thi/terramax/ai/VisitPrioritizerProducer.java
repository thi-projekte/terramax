package de.thi.terramax.ai;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import java.util.function.Supplier;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Wählt die {@link VisitPrioritizer}-Implementierung über {@code terramax.prioritizer.mode}: {@code stub} (Standard)
 * oder {@code llm}. Ein unbekannter Wert führt beim ersten Zugriff zu einer IllegalStateException.
 */
@ApplicationScoped
public class VisitPrioritizerProducer {

    static final String MODE_PROPERTY = "terramax.prioritizer.mode";

    @Produces
    @ApplicationScoped
    VisitPrioritizer prioritizer(@ConfigProperty(name = MODE_PROPERTY, defaultValue = "stub") String mode,
            Instance<LongestUnvisitedPrioritizer> stub, Instance<LlmVisitPrioritizer> llm) {
        return select(mode, stub::get, llm::get);
    }

    static VisitPrioritizer select(String mode, Supplier<? extends VisitPrioritizer> stub,
            Supplier<? extends VisitPrioritizer> llm) {
        return switch (mode.strip()) {
            case "stub" -> stub.get();
            case "llm" -> llm.get();
            default -> throw new IllegalStateException(MODE_PROPERTY + " muss stub oder llm sein, ist aber: " + mode);
        };
    }
}
