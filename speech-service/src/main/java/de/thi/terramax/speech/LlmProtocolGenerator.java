package de.thi.terramax.speech;

import com.fasterxml.jackson.core.JacksonException;
import de.thi.terramax.speech.ProtocolGenerationException.Reason;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.service.output.OutputParsingException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Erstellt das Protokoll über den LangChain4j-AI-Service und übersetzt dessen Fehler.
 */
@ApplicationScoped
public class LlmProtocolGenerator implements ProtocolGenerator {

    private final VisitProtocolAssistant assistant;

    @Inject
    public LlmProtocolGenerator(VisitProtocolAssistant assistant) {
        this.assistant = assistant;
    }

    @Override
    public VisitProtocol generate(String transcript, LocalDate today) {
        VisitProtocol protocol;
        try {
            protocol = assistant.createProtocol(transcript, today);
        } catch (RuntimeException e) {
            throw translate(e);
        }
        if (protocol == null) {
            throw new ProtocolGenerationException(Reason.INVALID_RESPONSE, "Leere Antwort des LLM");
        }
        // Listen nie null, damit die Oberfläche sie direkt anzeigen kann
        return new VisitProtocol(protocol.contactPerson(), clean(protocol.topics()), protocol.outcome(),
                clean(protocol.nextSteps()), protocol.followUpDate());
    }

    // Die Ursache wird bewusst nicht weitergegeben: Fehlertexte des Anbieters können Teile der Anfrage
    // (also des Transkripts) enthalten und würden sonst im Log landen.
    static ProtocolGenerationException translate(RuntimeException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpException http) {
                Reason reason = switch (http.statusCode()) {
                    case 401, 403 -> Reason.NOT_CONFIGURED;
                    case 429 -> Reason.RATE_LIMITED;
                    default -> Reason.UNAVAILABLE;
                };
                return new ProtocolGenerationException(reason, "LLM antwortet mit Status " + http.statusCode());
            }
            if (t instanceof OutputParsingException || t instanceof JacksonException
                    || t instanceof DateTimeParseException) {
                return new ProtocolGenerationException(Reason.INVALID_RESPONSE,
                        "Antwort des LLM passt nicht zum Protokoll");
            }
        }
        return new ProtocolGenerationException(Reason.UNAVAILABLE,
                "LLM nicht erreichbar (" + e.getClass().getSimpleName() + ")");
    }

    private static List<String> clean(List<String> items) {
        return items == null ? List.of() : items.stream().filter(s -> s != null && !s.isBlank()).toList();
    }
}
