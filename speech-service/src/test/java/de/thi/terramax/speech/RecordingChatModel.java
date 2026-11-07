package de.thi.terramax.speech;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.List;

/**
 * Ersetzt OpenAI in den Tests. Merkt sich die zuletzt gesendeten Nachrichten und antwortet mit einem festen Protokoll.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class RecordingChatModel implements ChatModel {

    private List<ChatMessage> lastMessages = List.of();

    List<ChatMessage> lastMessages() {
        return lastMessages;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        lastMessages = List.copyOf(request.messages());
        String json = """
                {"contactPerson": null, "topics": [], "outcome": null, "nextSteps": [], "followUpDate": null}
                """;
        return ChatResponse.builder().aiMessage(AiMessage.from(json)).build();
    }
}
