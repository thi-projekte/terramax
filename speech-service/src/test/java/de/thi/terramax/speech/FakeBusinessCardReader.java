package de.thi.terramax.speech;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * Ersetzt das LLM beim Lesen von Visitenkarten in den Tests. Merkt sich den letzten Aufruf und kann Fehler simulieren.
 * Zugriff nur über Methoden, weil Tests einen Proxy der Bean injiziert bekommen.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class FakeBusinessCardReader implements BusinessCardReader {

    private ProtocolGenerationException.Reason failWith;
    private byte[] lastImage;
    private String lastMediaType;

    void reset() {
        failWith = null;
        lastImage = null;
        lastMediaType = null;
    }

    void failWith(ProtocolGenerationException.Reason reason) {
        failWith = reason;
    }

    byte[] lastImage() {
        return lastImage;
    }

    String lastMediaType() {
        return lastMediaType;
    }

    @Override
    public BusinessCard read(byte[] image, String mediaType) {
        lastImage = image;
        lastMediaType = mediaType;
        if (failWith != null) {
            throw new ProtocolGenerationException(failWith, "simuliert");
        }
        return new BusinessCard("Josef Maier", "Hof Maier GbR", "Betriebsleiter", "+43 664 1234567",
                "maier@example.at", "Hauptstraße 1", "4600", "Wels");
    }
}
