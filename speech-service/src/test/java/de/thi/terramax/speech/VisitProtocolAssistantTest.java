package de.thi.terramax.speech;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stellt sicher, dass jede Sprachnotiz für sich ausgewertet wird: Transkripte früherer Anfragen (anderer Nutzer) dürfen
 * nicht in späteren Anfragen an das LLM landen.
 */
@QuarkusTest
class VisitProtocolAssistantTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 7);

    @Inject
    VisitProtocolAssistant assistant;

    @Inject
    RecordingChatModel chatModel;

    @Test
    void earlierTranscriptsAreNotSentAgain() {
        assistant.createProtocol("Besuch beim Hof Maier.", TODAY);
        assistant.createProtocol("Besuch beim Hof Huber.", TODAY);

        List<ChatMessage> messages = chatModel.lastMessages();
        String userText = messages.stream()
                .filter(UserMessage.class::isInstance)
                .map(m -> ((UserMessage) m).singleText())
                .reduce("", String::concat);

        assertEquals(2, messages.size(), "nur Systemnachricht und aktuelles Transkript");
        assertTrue(userText.contains("Hof Huber"));
        assertFalse(userText.contains("Hof Maier"));
    }
}
