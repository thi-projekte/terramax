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
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Importiert beim Start die Standorte aus den in {@code location.import.files} konfigurierten Classpath-Ressourcen. Ein
 * Standort wird nur angelegt, wenn seine externe ID noch nicht existiert; bestehende Einträge werden nicht
 * überschrieben. Dadurch kann der Import bei jedem Start laufen und ergänzt nur neue Standorte. Jede Datei wird in
 * einer eigenen Transaktion importiert. Abschaltbar über {@code location.import.enabled=false}.
 */
@ApplicationScoped
public class LocationImporter {

    static final String COMPETITORS = "data/competitors_at_v2.geojson";
    static final String JOHN_DEERE = "data/john_deere_at.geojson";

    private static final Logger LOG = Logger.getLogger(LocationImporter.class);

    private final ObjectMapper objectMapper;
    private final GeoJsonFeatureParser parser;

    @ConfigProperty(name = "location.import.enabled", defaultValue = "true")
    boolean importEnabled;

    @ConfigProperty(name = "location.import.files", defaultValue = COMPETITORS + "," + JOHN_DEERE)
    List<String> importFiles;

    @Inject
    LocationImporter(ObjectMapper objectMapper, GeoJsonFeatureParser parser) {
        this.objectMapper = objectMapper;
        this.parser = parser;
    }

    /** Ergebnis des Imports einer Datei. */
    record ImportResult(int created, int skipped, int invalid) {
    }

    void onStart(@Observes StartupEvent event) {
        if (!importEnabled) {
            LOG.info("Standort-Import ist deaktiviert (location.import.enabled=false)");
            return;
        }
        importFiles.forEach(this::importFile);
    }

    /**
     * Importiert alle gültigen Features der Classpath-Ressource, deren externe ID noch nicht existiert.
     *
     * @return Anzahl neu angelegter, übersprungener (ID bereits vorhanden) und ungültiger Einträge
     */
    @Transactional
    ImportResult importFile(String resource) {
        JsonNode features = readFeatures(resource);
        Set<String> existingIds = Location.findAllExternalIds();

        int created = 0;
        int skipped = 0;
        int invalid = 0;
        for (JsonNode feature : features) {
            Location location;
            try {
                location = parser.parse(feature);
            } catch (InvalidFeatureException e) {
                invalid++;
                LOG.warnf("%s: ungültiger Eintrag übersprungen: %s", resource, e.getMessage());
                continue;
            }
            // add() liefert false, wenn die ID schon in der Datenbank oder weiter oben in derselben Datei vorkam
            if (existingIds.add(location.externalId)) {
                location.persist();
                created++;
            } else {
                skipped++;
            }
        }
        LOG.infof("Standort-Import %s: %d neu, %d übersprungen (bereits vorhanden), %d ungültig", resource, created,
                skipped, invalid);
        return new ImportResult(created, skipped, invalid);
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
