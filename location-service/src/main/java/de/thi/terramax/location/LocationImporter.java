package de.thi.terramax.location;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Importiert beim Start die Standorte aus {@code data/competitors_at_v2.geojson}, sofern die Tabelle {@code location}
 * leer ist. Der Import läuft in einer Transaktion und ist über {@code location.import.enabled=false} abschaltbar.
 */
@ApplicationScoped
public class LocationImporter {

    static final String RESOURCE = "data/competitors_at_v2.geojson";

    private static final Logger LOG = Logger.getLogger(LocationImporter.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    GeoJsonFeatureParser parser;

    @ConfigProperty(name = "location.import.enabled", defaultValue = "true")
    boolean importEnabled;

    void onStart(@Observes StartupEvent event) {
        if (!importEnabled) {
            LOG.info("Standort-Import ist deaktiviert (location.import.enabled=false)");
            return;
        }
        importIfEmpty(RESOURCE);
    }

    /**
     * Importiert alle gültigen Features aus der Classpath-Ressource, sofern die Tabelle leer ist.
     *
     * @return Anzahl der importierten Einträge (0, wenn die Tabelle bereits Daten enthält)
     */
    @Transactional
    int importIfEmpty(String resource) {
        long existing = Location.count();
        if (existing > 0) {
            LOG.infof("Standort-Import übersprungen: Tabelle enthält bereits %d Einträge", existing);
            return 0;
        }

        int imported = 0;
        int skipped = 0;
        for (JsonNode feature : readFeatures(resource)) {
            try {
                parser.parse(feature).persist();
                imported++;
            } catch (InvalidFeatureException e) {
                skipped++;
                LOG.warnf("Ungültiger Eintrag übersprungen: %s", e.getMessage());
            }
        }
        LOG.infof("Standort-Import abgeschlossen: %d importiert, %d übersprungen", imported, skipped);
        return imported;
    }

    JsonNode readFeatures(String resource) {
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
