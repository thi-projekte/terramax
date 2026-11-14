package de.thi.terramax.speech;

import de.thi.terramax.speech.ProtocolGenerationException.Reason;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.service.output.OutputParsingException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Prüft Aufbereitung und Fehlerübersetzung ohne Quarkus und ohne echtes LLM.
 */
class LlmProtocolGeneratorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    private static LlmProtocolGenerator answering(VisitProtocol protocol) {
        return new LlmProtocolGenerator((transcript, today) -> protocol);
    }

    private static Reason reasonFor(RuntimeException error) {
        var generator = new LlmProtocolGenerator((transcript, today) -> {
            throw error;
        });
        return assertThrows(ProtocolGenerationException.class, () -> generator.generate("Text", TODAY)).reason();
    }

    @Test
    void missingListsBecomeEmptyAndBlankEntriesAreRemoved() {
        var protocol = answering(new VisitProtocol(null, null, null, Arrays.asList("Angebot", " ", null), null))
                .generate("Text", TODAY);

        assertNull(protocol.contactPerson());
        assertEquals(List.of(), protocol.topics());
        assertEquals(List.of("Angebot"), protocol.nextSteps());
        assertNull(protocol.followUpDate());
    }

    @Test
    void emptyAnswerIsInvalid() {
        var error = assertThrows(ProtocolGenerationException.class, () -> answering(null).generate("Text", TODAY));
        assertEquals(Reason.INVALID_RESPONSE, error.reason());
    }

    @Test
    void httpErrorsAreTranslated() {
        assertEquals(Reason.NOT_CONFIGURED, reasonFor(new HttpException(401, "invalid api key")));
        assertEquals(Reason.RATE_LIMITED, reasonFor(new HttpException(429, "quota")));
        assertEquals(Reason.UNAVAILABLE, reasonFor(new HttpException(500, "server error")));
        // LangChain4j verpackt Fehler teilweise
        assertEquals(Reason.RATE_LIMITED, reasonFor(new RuntimeException(new HttpException(429, "quota"))));
    }

    @Test
    void unparsableAnswerIsInvalid() {
        assertEquals(Reason.INVALID_RESPONSE, reasonFor(new OutputParsingException("kein JSON", null)));
    }

    @Test
    void otherErrorsMeanUnavailable() {
        assertEquals(Reason.UNAVAILABLE, reasonFor(new RuntimeException("Connection refused")));
    }

    @Test
    void providerTextIsNotPassedOn() {
        var error = assertThrows(ProtocolGenerationException.class, () -> new LlmProtocolGenerator((t, d) -> {
            throw new HttpException(400, "Transkript: Hof Maier ...");
        }).generate("Text", TODAY));
        assertFalse(error.getMessage().contains("Hof Maier"));
        assertNull(error.getCause());
    }
}
