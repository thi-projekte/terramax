package de.thi.terramax.speech;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Stellt sicher, dass jede Visitenkarte für sich gelesen wird: Fotos früherer Anfragen (anderer Nutzer) dürfen nicht in
 * späteren Anfragen an das LLM landen. Das Bild muss als Bildinhalt ankommen, nicht als Text.
 */
@QuarkusTest
class BusinessCardAssistantTest {

    @Inject
    BusinessCardAssistant assistant;

    @Inject
    RecordingChatModel chatModel;

    private static Image image(String base64) {
        return Image.builder().base64Data(base64).mimeType("image/jpeg").build();
    }

    @Test
    void earlierCardsAreNotSentAgain() {
        assistant.read(image("AAAA"));
        BusinessCard card = assistant.read(image("BBBB"));

        List<ChatMessage> messages = chatModel.lastMessages();
        List<ImageContent> images = messages.stream()
                .filter(UserMessage.class::isInstance)
                .flatMap(m -> ((UserMessage) m).contents().stream())
                .filter(ImageContent.class::isInstance)
                .map(ImageContent.class::cast)
                .toList();

        assertNotNull(card);
        assertEquals(2, messages.size(), "nur Systemnachricht und aktuelle Karte");
        assertEquals(1, images.size());
        assertEquals("BBBB", images.getFirst().image().base64Data());
    }
}
