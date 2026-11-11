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
    void importsAllConfiguredFilesAtStartup() {
        assertEquals(213, Location.count());
    }

    @Test
    void importsCategoriesCorrectly() {
        assertEquals(133, Location.count("category", LocationCategory.COMPETITOR));
        assertEquals(64, Location.count("category", LocationCategory.MIXED_COMPETITOR));
        assertEquals(16, Location.count("category", LocationCategory.JOHN_DEERE));
    }

    @Test
    void mapsGeoJsonCoordinatesToLatitudeAndLongitude() {
        Location location = Location.find("externalId", "res-0").firstResult();

        assertNotNull(location);
        assertEquals(48.629, location.latitude, DELTA);
        assertEquals(16.102, location.longitude, DELTA);
    }

    @Test
    void importsJohnDeereLocation() {
        Location location = Location.findByExternalId("jd-ltc-korneuburg").orElseThrow();

        assertEquals(LocationCategory.JOHN_DEERE, location.category);
        assertEquals(48.3365145, location.latitude, DELTA);
        assertEquals(16.338749, location.longitude, DELTA);
    }

    /** Der Start hat bereits importiert; zwei weitere Durchläufe legen nichts an. */
    @Test
    void importingTwiceCreatesNoDuplicates() {
        for (int run = 0; run < 2; run++) {
            assertEquals(new LocationImporter.ImportResult(0, 197, 0),
                    importer.importFile(LocationImporter.COMPETITORS));
            assertEquals(new LocationImporter.ImportResult(0, 16, 0), importer.importFile(LocationImporter.JOHN_DEERE));
        }
        assertEquals(213, Location.count());
    }

    /** Test-Transaktion wird zurückgerollt, die Änderung am Namen bleibt also nicht bestehen. */
    @Test
    @TestTransaction
    void doesNotOverwriteExistingLocations() {
        Location location = Location.findByExternalId("jd-ltc-korneuburg").orElseThrow();
        location.name = "Manuell geändert";

        importer.importFile(LocationImporter.JOHN_DEERE);
        // Neu aus der Datenbank lesen statt aus dem Persistence-Context
        Location.getEntityManager().flush();
        Location.getEntityManager().clear();

        assertEquals("Manuell geändert", Location.findByExternalId("jd-ltc-korneuburg").orElseThrow().name);
    }

    @Test
    @TestTransaction
    void importsOnlyMissingLocations() {
        Location.delete("externalId", "jd-ltc-korneuburg");

        assertEquals(new LocationImporter.ImportResult(1, 15, 0), importer.importFile(LocationImporter.JOHN_DEERE));
        assertEquals(213, Location.count());
    }

    @Test
    @TestTransaction
    void skipsInvalidFeaturesAndImportsValidOnes() {
        assertEquals(new LocationImporter.ImportResult(1, 0, 2), importer.importFile("data/test-mixed.geojson"));
        assertEquals(214, Location.count());
        assertNotNull(Location.find("externalId", "valid-1").firstResult());
    }

    @Test
    @TestTransaction
    void skipsDuplicateIdsWithinOneFile() {
        assertEquals(new LocationImporter.ImportResult(1, 1, 0),
                importer.importFile("data/test-duplicate-ids.geojson"));
        assertEquals("Erster Eintrag", Location.findByExternalId("dup-1").orElseThrow().name);
    }
}
