package de.thi.terramax.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Liest die {@code features} einer GeoJSON-FeatureCollection aus einer Classpath-Ressource (für die Importe beim
 * Start).
 */
public final class GeoJsonResources {

    private GeoJsonResources() {
    }

    /**
     * @throws IllegalStateException
     *             wenn die Ressource fehlt oder kein {@code features}-Array enthält
     * @throws UncheckedIOException
     *             wenn die Datei kein gültiges JSON ist
     */
    public static JsonNode readFeatures(ObjectMapper objectMapper, String resource) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Ressource nicht gefunden: " + resource);
            }
            JsonNode features = objectMapper.readTree(in).path("features");
            if (!features.isArray()) {
                throw new IllegalStateException(resource + " enthält kein features-Array");
            }
            return features;
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte " + resource + " nicht lesen", e);
        }
    }
}
