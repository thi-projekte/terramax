package de.thi.terramax.speech;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Events auf terramax.speech.events nach dem Vertrag in docs/kafka/README.md (CloudEvent, Key = visitId, keine
 * Inhalte).
 */
@QuarkusTest
@TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
class SpeechEventsTest {

    @Inject
    FakeSpeechToTextClient speechToText;

    @Inject
    FakeProtocolGenerator generator;

    @Inject
    EventSink events;

    @BeforeEach
    void reset() {
        speechToText.reset();
        generator.reset();
        events.clear();
    }

    private static void transcribe(int expectedStatus) {
        given().multiPart("audio", "voice-note.webm", new byte[] { 1, 2, 3 }, "audio/webm")
                .multiPart("visitId", "7:5")
                .when().post("/transcriptions")
                .then().statusCode(expectedStatus);
    }

    private static void draft(int expectedStatus) {
        given().contentType(ContentType.JSON).body(Map.of("visitId", "7:5", "transcript", "Besuch beim Hof Maier."))
                .when().post("/protocols")
                .then().statusCode(expectedStatus);
    }

    @Test
    void transcriptionCompletedIsACloudEvent() {
        transcribe(200);

        assertEquals(List.of("transcription.completed"), events.types());
        assertEquals(List.of("7:5"), events.keys());
        JsonNode event = events.events().getFirst();
        assertEquals("1.0", event.get("specversion").asText());
        assertEquals("/speech-service", event.get("source").asText());
        assertEquals("7:5", event.get("subject").asText());
        assertEquals("sales", event.get("userid").asText());
        assertEquals("application/json", event.get("datacontenttype").asText());
        assertFalse(event.get("id").asText().isBlank());
        assertFalse(event.get("time").asText().isBlank());
        assertEquals("de", event.get("data").get("language").asText());
        assertEquals(12.5, event.get("data").get("durationSeconds").asDouble());
        assertEquals(null, event.get("data").get("transcript"), "Transkript gehört nicht ins Event");
    }

    @Test
    void transcriptionFailedCarriesStatus() {
        speechToText.failWith(SpeechToTextException.Reason.RATE_LIMITED);

        transcribe(503);

        assertEquals(List.of("transcription.failed"), events.types());
        assertEquals(503, events.events().getFirst().get("data").get("status").asInt());
    }

    @Test
    void protocolDrafted() {
        draft(200);

        assertEquals(List.of("protocol.drafted"), events.types());
        assertEquals(List.of("7:5"), events.keys());
    }

    @Test
    void protocolFailedCarriesStatus() {
        generator.failWith(ProtocolGenerationException.Reason.NOT_CONFIGURED);

        draft(502);

        assertEquals(List.of("protocol.failed"), events.types());
        assertEquals(502, events.events().getFirst().get("data").get("status").asInt());
    }
}
