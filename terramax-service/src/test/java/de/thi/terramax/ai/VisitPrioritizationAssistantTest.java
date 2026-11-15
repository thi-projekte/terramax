package de.thi.terramax.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.thi.terramax.ai.VisitPrioritizer.Candidate;
import de.thi.terramax.ai.VisitPrioritizer.PlanningConstraints;
import de.thi.terramax.ai.VisitPrioritizer.PrioritizedCustomer;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * LLM-Priorisierung über den echten LangChain4j-AI-Service, aber mit {@link RecordingChatModel} statt OpenAI.
 */
@QuarkusTest
class VisitPrioritizationAssistantTest {

    private static final PlanningConstraints CONSTRAINTS = new PlanningConstraints(LocalDate.of(2026, 11, 20), 5);

    @Inject
    LlmVisitPrioritizer llm;

    @Inject
    VisitPrioritizer configured;

    @Inject
    RecordingChatModel chatModel;

    @Test
    void structuredAnswerIsMappedToRanks() {
        List<PrioritizedCustomer> result = llm.prioritize(
                List.of(new Candidate(1, "Marchfeld", "Ackerbau", null), new Candidate(2, "Weinviertel", null, null)),
                CONSTRAINTS);

        assertEquals(List.of(new PrioritizedCustomer(2, 1, "Lange nicht besucht"),
                new PrioritizedCustomer(1, 2, "Saison")), result);
    }

    @Test
    void earlierRequestsAreNotSentAgain() {
        llm.prioritize(List.of(new Candidate(1, "Marchfeld", "Ackerbau", null)), CONSTRAINTS);
        llm.prioritize(List.of(new Candidate(2, "Weinviertel", "Grünland", null)), CONSTRAINTS);

        List<ChatMessage> messages = chatModel.lastMessages();
        String userText = messages.stream().filter(UserMessage.class::isInstance)
                .map(m -> ((UserMessage) m).singleText()).reduce("", String::concat);

        assertEquals(2, messages.size(), "nur Systemnachricht und aktuelle Anfrage");
        assertTrue(userText.contains("2 | Weinviertel | Grünland | nie"));
        assertFalse(userText.contains("Marchfeld"));
    }

    @Test
    void stubIsTheDefault() {
        List<PrioritizedCustomer> result = configured.prioritize(List.of(new Candidate(1, null, null, null)),
                CONSTRAINTS);
        assertEquals(LongestUnvisitedPrioritizer.NEVER_VISITED, result.get(0).reason());
    }
}
