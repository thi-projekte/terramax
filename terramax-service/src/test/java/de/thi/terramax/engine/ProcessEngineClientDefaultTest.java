package de.thi.terramax.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Ohne Konfiguration ist der Platzhalter aktiv: Es wird keine Engine aufgerufen.
 */
@QuarkusTest
class ProcessEngineClientDefaultTest {

    @Inject
    ProcessEngineClient client;

    @Test
    void stubIsTheDefault() {
        assertTrue(client.startTour(1).startsWith(StubProcessEngineClient.ID_PREFIX));
    }
}
