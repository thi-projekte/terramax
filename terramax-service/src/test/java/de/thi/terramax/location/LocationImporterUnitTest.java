package de.thi.terramax.location;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit-Tests für {@link LocationImporter} ohne Quarkus und ohne Datenbank. */
class LocationImporterUnitTest {

    private LocationImporter importer;

    @BeforeEach
    void setUp() {
        importer = new LocationImporter(new ObjectMapper(), new GeoJsonFeatureParser());
    }

    @Test
    void doesNothingWhenImportIsDisabled() {
        importer.importEnabled = false;

        // Ohne Quarkus würde jeder Datenbankzugriff (Location.count()) fehlschlagen
        assertDoesNotThrow(() -> importer.onStart(null));
    }

    @Test
    void readsFeaturesFromBundledResources() {
        assertEquals(197, importer.readFeatures(LocationImporter.COMPETITORS).size());
        assertEquals(16, importer.readFeatures(LocationImporter.JOHN_DEERE).size());
    }

    @Test
    void johnDeereFileContainsOnlyValidPointsInAustria() {
        for (var feature : importer.readFeatures(LocationImporter.JOHN_DEERE)) {
            String id = feature.path("properties").path("id").asText();
            assertEquals("Point", feature.path("geometry").path("type").asText(), id);
            double lon = feature.path("geometry").path("coordinates").get(0).asDouble();
            double lat = feature.path("geometry").path("coordinates").get(1).asDouble();
            assertTrue(lon >= 9.5 && lon <= 17.2, id + ": Länge " + lon);
            assertTrue(lat >= 46.3 && lat <= 49.1, id + ": Breite " + lat);
            assertTrue(feature.path("properties").path("geocode_hint").isMissingNode(), id + ": geocode_hint");
        }
    }

    @Test
    void failsWhenResourceIsMissing() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> importer.readFeatures("data/does-not-exist.geojson"));
        assertTrue(e.getMessage().contains("nicht gefunden"));
    }

    @Test
    void failsWhenResourceIsNotValidJson() {
        assertThrows(UncheckedIOException.class, () -> importer.readFeatures("data/test-invalid-json.geojson"));
    }

    @Test
    void failsWhenResourceHasNoFeaturesArray() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> importer.readFeatures("data/test-no-features.geojson"));
        assertTrue(e.getMessage().contains("features"));
    }
}
