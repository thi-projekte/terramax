package de.thi.terramax.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.thi.terramax.engine.ProcessEngineClient.ProcessStatus;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Platzhalter, REST-Anbindung (gegen eine Fake-API) und Auswahl über die Property, ohne Quarkus.
 */
class ProcessEngineClientTest {

    /** Fake der CIB-Seven-API: merkt sich Aufrufe, Antworten sind pro Test einstellbar. */
    private static class FakeApi implements CibSevenApi {

        final List<StartRequest> started = new ArrayList<>();
        final List<String> keys = new ArrayList<>();
        StartedInstance startAnswer = new StartedInstance("pi-1");
        RuntimeException error;
        InstanceState state = new InstanceState("pi-1", false, false);

        @Override
        public StartedInstance start(String processDefinitionKey, StartRequest request) {
            if (error != null) {
                throw error;
            }
            keys.add(processDefinitionKey);
            started.add(request);
            return startAnswer;
        }

        @Override
        public InstanceState processInstance(String processInstanceId) {
            if (error != null) {
                throw error;
            }
            return state;
        }
    }

    private final FakeApi api = new FakeApi();
    private final RestProcessEngineClient rest = new RestProcessEngineClient(api, "Besuchstour");

    @Test
    void stubReturnsFakeIdAndActiveStatus() {
        var stub = new StubProcessEngineClient();
        assertTrue(stub.startTour(7).startsWith(StubProcessEngineClient.ID_PREFIX));
        assertEquals(ProcessStatus.ACTIVE, stub.getStatus("stub-x"));
    }

    @Test
    void restStartsProcessWithTourIdAsBusinessKeyAndVariable() {
        assertEquals("pi-1", rest.startTour(42));
        assertEquals(List.of("Besuchstour"), api.keys);
        assertEquals("42", api.started.get(0).businessKey());
        assertEquals(new CibSevenApi.Variable(42L, "Long"), api.started.get(0).variables().get("tourId"));
    }

    @Test
    void restWithoutInstanceIdIsAnError() {
        api.startAnswer = new CibSevenApi.StartedInstance(null);
        assertThrows(ProcessEngineException.class, () -> rest.startTour(1));
    }

    @Test
    void restMapsInstanceState() {
        assertEquals(ProcessStatus.ACTIVE, rest.getStatus("pi-1"));
        api.state = new CibSevenApi.InstanceState("pi-1", false, true);
        assertEquals(ProcessStatus.SUSPENDED, rest.getStatus("pi-1"));
        api.state = new CibSevenApi.InstanceState("pi-1", true, false);
        assertEquals(ProcessStatus.ENDED, rest.getStatus("pi-1"));
    }

    @Test
    void restUnknownInstanceCountsAsEnded() {
        api.error = new WebApplicationException(404);
        assertEquals(ProcessStatus.ENDED, rest.getStatus("pi-weg"));
    }

    @Test
    void restTranslatesErrors() {
        api.error = new WebApplicationException(403);
        assertEquals("CIB Seven antwortet mit Status 403",
                assertThrows(ProcessEngineException.class, () -> rest.startTour(1)).getMessage());

        api.error = new ProcessingException("connection refused");
        assertEquals("CIB Seven nicht erreichbar",
                assertThrows(ProcessEngineException.class, () -> rest.getStatus("pi-1")).getMessage());
    }

    @Test
    void producerSelectsByMode() {
        var stub = new StubProcessEngineClient();
        assertSame(stub, ProcessEngineClientProducer.select("stub", () -> stub, () -> rest));
        assertSame(rest, ProcessEngineClientProducer.select("rest", () -> stub, () -> rest));
        assertThrows(IllegalStateException.class, () -> ProcessEngineClientProducer.select("kafka", () -> stub,
                () -> rest));
    }
}
