package de.thi.terramax.speech;

import de.thi.terramax.speech.SpeechToTextException.Reason;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der echte Transkriptions-Client gegen {@link FakeOpenAi}: was an OpenAI geht und wie Antworten und Fehler übersetzt
 * werden. Die übrigen Tests ersetzen den Client durch {@link FakeSpeechToTextClient}.
 */
@QuarkusTest
class OpenAiTranscriptionClientTest {

    @Inject
    OpenAiTranscriptionClient client;

    @Inject
    FakeOpenAi openAi;

    private Path audio;

    @BeforeEach
    void setUp() throws IOException {
        openAi.reset();
        audio = Files.createTempFile("voice-note-", ".webm");
        Files.write(audio, new byte[] { 1, 2, 3, 4, 5 });
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(audio);
    }

    @Test
    void sendsAudioWithModelLanguageKeywordsAndKey() {
        client.transcribe(audio, "voice-note.webm", "audio/webm");

        FakeOpenAi.Request request = openAi.lastRequest();
        assertEquals("Bearer test-key", request.authorization(), "derselbe Key wie für die Protokolle");
        assertEquals("gpt-transcribe", request.model());
        assertEquals("json", request.responseFormat());
        assertEquals(List.of("de"), request.languages());
        assertTrue(request.keywords().contains("8R 410"));
        assertTrue(request.keywords().contains("Operations Center"));
        assertTrue(request.prompt().startsWith("Besuchsprotokoll"));
        assertEquals("voice-note.webm", request.fileName());
        assertEquals("audio/webm", request.fileType());
        assertEquals(5, request.fileSize());
    }

    @Test
    void mapsAnswer() {
        Transcription transcription = client.transcribe(audio, "voice-note.webm", "audio/webm");

        assertEquals("Besuch beim Hof Maier.", transcription.transcript());
        assertEquals("de", transcription.language());
        assertNull(transcription.durationSeconds(), "gpt-transcribe rechnet nach Tokens ab, ohne Dauer");
    }

    @Test
    void usesDurationWhenBilledBySeconds() {
        openAi.answerWith(Map.of("text", "Hallo", "usage", Map.of("type", "duration", "seconds", 27.5)));

        Transcription transcription = client.transcribe(audio, "voice-note.webm", "audio/webm");

        assertEquals(27.5, transcription.durationSeconds());
        assertEquals("de", transcription.language(), "ohne erkannte Sprache die konfigurierte");
    }

    @ParameterizedTest
    @CsvSource({ "400, UNSUPPORTED_AUDIO", "401, UNAVAILABLE", "403, UNAVAILABLE", "429, RATE_LIMITED",
            "500, UNAVAILABLE", "503, UNAVAILABLE" })
    void translatesErrors(int status, Reason expected) {
        openAi.failWith(status);

        SpeechToTextException e = assertThrows(SpeechToTextException.class,
                () -> client.transcribe(audio, "voice-note.webm", "audio/webm"));

        assertEquals(expected, e.reason());
    }
}
