package de.thi.terramax.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.websockets.next.BasicWebSocketConnector;
import io.vertx.core.buffer.Buffer;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Transkription per WebSocket: Audio in Stücken, Zwischenstand, Ergebnis oder Fehler.
 */
@QuarkusTest
class TranscriptionSocketTest {

    private static final String STOP = "{\"type\":\"stop\",\"mediaType\":\"audio/webm;codecs=opus\"}";

    @TestHTTPResource("/")
    URI baseUri;

    @Inject
    FakeSpeechToTextClient speechToText;

    @Inject
    EventSink events;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        speechToText.reset();
        events.clear();
    }

    /** Schickt die Aufnahme in zwei Stücken und liefert alle Antworten bis zum Schließen. */
    private List<JsonNode> stream(String visitId) throws InterruptedException {
        List<JsonNode> messages = new CopyOnWriteArrayList<>();
        CountDownLatch closed = new CountDownLatch(1);
        var connection = BasicWebSocketConnector.create()
                .baseUri(baseUri)
                .path("/transcriptions/stream/" + visitId)
                .onTextMessage((c, text) -> messages.add(readJson(text)))
                .onClose((c, reason) -> closed.countDown())
                .connectAndAwait();

        connection.sendBinaryAndAwait(Buffer.buffer(new byte[] { 1, 2 }));
        connection.sendBinaryAndAwait(Buffer.buffer(new byte[] { 3, 4 }));
        connection.sendTextAndAwait(STOP);

        assertTrue(closed.await(10, TimeUnit.SECONDS), "Server schließt nach dem Ergebnis");
        return messages;
    }

    private JsonNode readJson(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void streamsProgressAndResult() throws InterruptedException {
        List<JsonNode> messages = stream("7:5");

        assertEquals(2, messages.size());
        assertEquals("progress", messages.get(0).get("type").asText());
        assertEquals("TRANSCRIBING", messages.get(0).get("step").asText());
        JsonNode result = messages.get(1);
        assertEquals("result", result.get("type").asText());
        assertEquals("7:5", result.get("result").get("visitId").asText());
        assertEquals("Besuch beim Hof Maier.", result.get("result").get("transcript").asText());

        assertEquals("audio/webm", speechToText.lastMediaType());
        assertEquals(List.of("transcription.completed"), events.types());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void reportsErrors() throws InterruptedException {
        speechToText.failWith(SpeechToTextException.Reason.RATE_LIMITED);

        JsonNode error = stream("7:5").getLast();

        assertEquals("error", error.get("type").asText());
        assertEquals(503, error.get("status").asInt());
        assertEquals(SpeechToTextException.Reason.RATE_LIMITED.userMessage(), error.get("message").asText());
    }

    @Test
    void rejectsAnonymousHandshake() {
        assertThrows(RuntimeException.class, () -> BasicWebSocketConnector.create()
                .baseUri(baseUri)
                .path("/transcriptions/stream/7:5")
                .connectAndAwait());
    }
}
