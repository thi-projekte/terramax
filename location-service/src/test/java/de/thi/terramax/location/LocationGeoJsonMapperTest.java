package de.thi.terramax.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reiner Unit-Test ohne Quarkus und Datenbank. */
class LocationGeoJsonMapperTest {

    private final LocationGeoJsonMapper mapper = new LocationGeoJsonMapper();

    private static Location fullLocation() {
        Location location = new Location();
        location.id = 42L;
        location.externalId = "res-21";
        location.name = "Christoph Heindl Landtechnik GmbH – Filiale Inning";
        location.category = LocationCategory.COMPETITOR;
        location.brands = "CLAAS";
        location.brandKnown = true;
        location.street = "Betriebsgebiet Inning 1/6";
        location.postcode = "3383";
        location.city = "Hürm";
        location.latitude = 48.183382;
        location.longitude = 15.410884;
        location.sourceType = "web_research";
        location.sourceUrl = "https://www.landtechnik-heindl.at/kontakt/";
        location.moreSources = "https://example.org/a";
        location.retrievedAt = LocalDate.of(2026, 11, 2);
        location.verified = false;
        location.note = "Koordinate manuell auf OSM ermittelt";
        location.geocodedBy = "manuell (OSM)";
        return location;
    }

    @Test
    void mapsAllFieldsAndPutsLongitudeFirst() {
        GeoJsonFeature feature = mapper.toFeature(fullLocation());

        assertEquals("Feature", feature.type());
        assertEquals("Point", feature.geometry().type());
        assertEquals(List.of(15.410884, 48.183382), feature.geometry().coordinates());

        LocationProperties p = feature.properties();
        assertEquals("res-21", p.id());
        assertEquals("Christoph Heindl Landtechnik GmbH – Filiale Inning", p.name());
        assertEquals("competitor", p.category());
        assertEquals("CLAAS", p.brands());
        assertTrue(p.brandKnown());
        assertEquals("Betriebsgebiet Inning 1/6", p.street());
        assertEquals("3383", p.postcode());
        assertEquals("Hürm", p.city());
        assertEquals("web_research", p.sourceType());
        assertEquals("https://www.landtechnik-heindl.at/kontakt/", p.sourceUrl());
        assertEquals("https://example.org/a", p.moreSources());
        assertEquals("2026-11-02", p.retrievedAt());
        assertFalse(p.verified());
        assertEquals("Koordinate manuell auf OSM ermittelt", p.note());
    }

    @Test
    void mapsMissingOptionalFieldsToNull() {
        Location location = new Location();
        location.externalId = "osm-node-1";
        location.name = "Nur Pflichtfelder";
        location.category = LocationCategory.MIXED_COMPETITOR;
        location.latitude = 47.0;
        location.longitude = 13.0;

        LocationProperties p = mapper.toFeature(location).properties();

        assertEquals("osm-node-1", p.id());
        assertEquals("mixed_competitor", p.category());
        assertNull(p.brands());
        assertNull(p.brandKnown());
        assertNull(p.street());
        assertNull(p.postcode());
        assertNull(p.city());
        assertNull(p.sourceType());
        assertNull(p.sourceUrl());
        assertNull(p.moreSources());
        assertNull(p.retrievedAt());
        assertNull(p.verified());
        assertNull(p.note());
    }

    @Test
    void mapsMissingCategoryToNull() {
        Location location = fullLocation();
        location.category = null;

        assertNull(mapper.toFeature(location).properties().category());
    }

    @Test
    void mapsListToFeatureCollectionInSameOrder() {
        Location second = fullLocation();
        second.externalId = "res-22";

        GeoJsonFeatureCollection collection = mapper.toFeatureCollection(List.of(fullLocation(), second));

        assertEquals("FeatureCollection", collection.type());
        assertEquals(List.of("res-21", "res-22"),
                collection.features().stream().map(f -> f.properties().id()).toList());
    }

    @Test
    void mapsEmptyListToEmptyFeatureCollection() {
        assertTrue(mapper.toFeatureCollection(List.of()).features().isEmpty());
    }

    @Test
    void serializesPropertiesInSnakeCaseIncludingNulls() {
        Location location = fullLocation();
        location.note = null;

        JsonNode json = new ObjectMapper().valueToTree(mapper.toFeature(location));
        JsonNode props = json.get("properties");

        for (String field : List.of("id", "name", "category", "brands", "brand_known", "street", "postcode", "city",
                "source_type", "source_url", "more_sources", "retrieved_at", "verified", "note")) {
            assertTrue(props.has(field), "Feld fehlt: " + field);
        }
        assertEquals(14, props.size());
        assertTrue(props.get("note").isNull());
        assertEquals("2026-11-02", props.get("retrieved_at").asText());
        assertEquals(15.410884, json.get("geometry").get("coordinates").get(0).asDouble());
    }
}
