package de.thi.terramax.speech;

/**
 * Liest die Daten einer fotografierten Visitenkarte. Die Resource kennt nur dieses Interface, damit Tests ohne LLM
 * auskommen.
 */
public interface BusinessCardReader {

    /**
     * @param image
     *            Bilddaten (JPEG, PNG oder WebP)
     * @param mediaType
     *            z. B. {@code image/jpeg}
     *
     * @throws ProtocolGenerationException
     *             wenn das LLM nicht erreichbar ist oder unbrauchbar antwortet
     */
    BusinessCard read(byte[] image, String mediaType);
}
