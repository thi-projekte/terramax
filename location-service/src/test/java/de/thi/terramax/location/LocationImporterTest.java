package de.thi.terramax.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LocationImporterTest {

    private static final double DELTA = 0.001;

    @Inject
    LocationImporter importer;

    @Test
    void importsAllFeaturesAtStartup() {
        assertEquals(197, Location.count());
    }

    @Test
    void importsCategoriesCorrectly() {
        assertEquals(133, Location.count("category", LocationCategory.COMPETITOR));
        assertEquals(64, Location.count("category", LocationCategory.MIXED_COMPETITOR));
        assertEquals(0, Location.count("category", LocationCategory.JOHN_DEERE));
    }

    @Test
    void mapsGeoJsonCoordinatesToLatitudeAndLongitude() {
        Location location = Location.find("externalId", "res-0").firstResult();

        assertNotNull(location);
        assertEquals(48.629, location.latitude, DELTA);
        assertEquals(16.102, location.longitude, DELTA);
    }

    @Test
    void skipsImportWhenTableIsNotEmpty() {
        assertEquals(0, importer.importIfEmpty(LocationImporter.RESOURCE));
        assertEquals(197, Location.count());
    }

    /** Läuft in einer Test-Transaktion, die am Ende zurückgerollt wird; die 197 Einträge bleiben erhalten. */
    @Test
    @TestTransaction
    void skipsInvalidFeaturesAndImportsValidOnes() {
        Location.deleteAll();

        int imported = importer.importIfEmpty("data/test-mixed.geojson");

        assertEquals(1, imported);
        assertEquals(1, Location.count());
        assertNotNull(Location.find("externalId", "valid-1").firstResult());
    }
}
