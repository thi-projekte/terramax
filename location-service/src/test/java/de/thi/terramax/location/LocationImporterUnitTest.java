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
        importer = new LocationImporter();
        importer.objectMapper = new ObjectMapper();
        importer.parser = new GeoJsonFeatureParser();
    }

    @Test
    void doesNothingWhenImportIsDisabled() {
        importer.importEnabled = false;

        // Ohne Quarkus würde jeder Datenbankzugriff (Location.count()) fehlschlagen
        assertDoesNotThrow(() -> importer.onStart(null));
    }

    @Test
    void readsFeaturesFromBundledResource() {
        assertEquals(197, importer.readFeatures(LocationImporter.RESOURCE).size());
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
