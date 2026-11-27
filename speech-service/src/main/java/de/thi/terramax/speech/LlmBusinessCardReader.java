package de.thi.terramax.speech;

import de.thi.terramax.speech.ProtocolGenerationException.Reason;
import dev.langchain4j.data.image.Image;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Base64;

/**
 * Liest Visitenkarten über den LangChain4j-AI-Service. Fehler übersetzt {@link LlmProtocolGenerator#translate}, damit
 * die SPA dieselben Status wie beim Protokoll bekommt (502, 503).
 */
@ApplicationScoped
public class LlmBusinessCardReader implements BusinessCardReader {

    private final BusinessCardAssistant assistant;

    @Inject
    public LlmBusinessCardReader(BusinessCardAssistant assistant) {
        this.assistant = assistant;
    }

    @Override
    public BusinessCard read(byte[] image, String mediaType) {
        Image picture = Image.builder().base64Data(Base64.getEncoder().encodeToString(image)).mimeType(mediaType)
                .build();
        BusinessCard card;
        try {
            card = assistant.read(picture);
        } catch (RuntimeException e) {
            throw LlmProtocolGenerator.translate(e);
        }
        if (card == null) {
            throw new ProtocolGenerationException(Reason.INVALID_RESPONSE, "Leere Antwort des LLM");
        }
        return new BusinessCard(clean(card.contactName()), clean(card.company()), clean(card.role()),
                clean(card.phone()), clean(card.email()), clean(card.street()), clean(card.postalCode()),
                clean(card.city()));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
