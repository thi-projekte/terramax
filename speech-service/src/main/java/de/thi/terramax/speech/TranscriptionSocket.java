package de.thi.terramax.speech;

import io.quarkus.websockets.next.OnBinaryMessage;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.vertx.core.buffer.Buffer;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sprachnotiz als Stream: Die SPA schickt die Aufnahme schon während des Sprechens in Stücken (Binary Messages) und am
 * Ende {@code {"type": "stop", "mediaType": "audio/webm"}}. Der Service antwortet mit {@code {"type": "progress",
 * "step": "TRANSCRIBING"}}, dann mit {@code {"type": "result", "result": {...}}} (Felder wie bei
 * {@code POST /transcriptions}) oder {@code {"type": "error", "status": 502, "message": "..."}} und schließt die
 * Verbindung.
 * <p>
 * Browser können beim Verbindungsaufbau keinen Authorization-Header setzen. Das Token kommt deshalb als Subprotocol
 * {@code quarkus-http-upgrade#Authorization#Bearer%20<token>} (siehe README). Audio wird nur als temporäre Datei
 * gehalten und beim Schließen gelöscht.
 */
@WebSocket(path = "/transcriptions/stream/{visitId}")
@RolesAllowed(Roles.SALES_PROFESSIONAL)
public class TranscriptionSocket {

    /** Wie beim REST-Upload (quarkus.http.limits.max-body-size). */
    static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;

    @Inject
    Transcriber transcriber;

    /** Aufnahmen der offenen Verbindungen, je Verbindung eine temporäre Datei. */
    private final Map<String, Path> recordings = new ConcurrentHashMap<>();

    public record Command(String type, String mediaType) {
    }

    public record Progress(String type, String step) {
    }

    public record Result(String type, TranscriptionResource.TranscriptionResponse result) {
    }

    public record Failure(String type, int status, String message) {
    }

    @OnOpen
    void open(WebSocketConnection connection) throws IOException {
        recordings.put(connection.id(), Files.createTempFile("voice-note-", ".audio"));
    }

    @OnBinaryMessage
    void chunk(Buffer chunk, WebSocketConnection connection) throws IOException {
        Path file = recordings.get(connection.id());
        if (file == null) {
            return;
        }
        if (Files.size(file) + chunk.length() > MAX_AUDIO_BYTES) {
            fail(connection, 413, "Die Aufnahme ist zu groß (max. 10 MB).");
            return;
        }
        Files.write(file, chunk.getBytes(), StandardOpenOption.APPEND);
    }

    @OnTextMessage
    void command(Command command, WebSocketConnection connection) throws IOException {
        Path file = recordings.get(connection.id());
        if (file == null || command == null || !"stop".equals(command.type())) {
            return;
        }
        String mediaType = Transcriber.mediaType(command.mediaType());
        if (!mediaType.startsWith("audio/")) {
            fail(connection, 415, "Die Datei ist keine Audiodatei.");
            return;
        }
        if (Files.size(file) == 0) {
            fail(connection, 400, "Es wurde keine Audiodatei übertragen.");
            return;
        }

        String visitId = connection.pathParam("visitId");
        connection.sendTextAndAwait(new Progress("progress", "TRANSCRIBING"));
        try {
            Transcription transcription = transcriber.transcribe(visitId, file,
                    "voice-note." + mediaType.substring("audio/".length()), mediaType);
            connection.sendTextAndAwait(
                    new Result("result", TranscriptionResource.TranscriptionResponse.of(visitId, transcription)));
            connection.closeAndAwait();
        } catch (SpeechToTextException e) {
            fail(connection, e.reason().status(), e.reason().userMessage());
        }
    }

    @OnClose
    void close(WebSocketConnection connection) throws IOException {
        Path file = recordings.remove(connection.id());
        if (file != null) {
            Files.deleteIfExists(file);
        }
    }

    private static void fail(WebSocketConnection connection, int status, String message) {
        connection.sendTextAndAwait(new Failure("error", status, message));
        connection.closeAndAwait();
    }
}
