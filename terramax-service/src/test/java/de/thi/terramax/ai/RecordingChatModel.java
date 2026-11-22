package de.thi.terramax.ai;

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
 * Ersetzt OpenAI in den Tests (wie im speech-service). Merkt sich die zuletzt gesendeten Nachrichten und antwortet mit
 * einer festen Priorisierung.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class RecordingChatModel implements ChatModel {

    static final String ANSWER = """
            {"customers": [{"customerId": 2, "reason": "Lange nicht besucht"}, {"customerId": 1, "reason": "Saison"}]}
            """;

    private List<ChatMessage> lastMessages = List.of();

    List<ChatMessage> lastMessages() {
        return lastMessages;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        lastMessages = List.copyOf(request.messages());
        return ChatResponse.builder().aiMessage(AiMessage.from(ANSWER)).build();
    }
}
