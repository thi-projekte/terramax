package de.thi.terramax.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import java.util.function.Supplier;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Wählt die {@link ProcessEngineClient}-Implementierung über {@code terramax.engine.mode}: {@code stub} (Standard) oder
 * {@code rest}. Ein unbekannter Wert führt beim ersten Zugriff zu einer IllegalStateException.
 */
@ApplicationScoped
public class ProcessEngineClientProducer {

    static final String MODE_PROPERTY = "terramax.engine.mode";

    @Produces
    @ApplicationScoped
    ProcessEngineClient processEngineClient(@ConfigProperty(name = MODE_PROPERTY, defaultValue = "stub") String mode,
            Instance<StubProcessEngineClient> stub, Instance<RestProcessEngineClient> rest) {
        return select(mode, stub::get, rest::get);
    }

    static ProcessEngineClient select(String mode, Supplier<? extends ProcessEngineClient> stub,
            Supplier<? extends ProcessEngineClient> rest) {
        return switch (mode.strip()) {
            case "stub" -> stub.get();
            case "rest" -> rest.get();
            default -> throw new IllegalStateException(MODE_PROPERTY + " muss stub oder rest sein, ist aber: " + mode);
        };
    }
}
