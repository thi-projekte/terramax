package de.thi.terramax.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Unit-Test ohne Quarkus-Start. */
class GeoJsonFeatureParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final GeoJsonFeatureParser parser = new GeoJsonFeatureParser();

    @Test
    void parsesSingleFeature() throws Exception {
        JsonNode feature = mapper.readTree("""
                {
                  "type": "Feature",
                  "geometry": { "type": "Point", "coordinates": [16.1016403, 48.6291475] },
                  "properties": {
                    "id": "res-0",
                    "name": "Landtechnik Schuster",
                    "category": "mixed_competitor",
                    "brands": "CLAAS",
                    "brand_known": true,
                    "street": "Grund 160",
                    "postcode": "2041",
                    "city": "Grund",
                    "source_type": "web_research+osm",
                    "source_url": "https://www.landtechnik-schuster.at/",
                    "more_sources": "",
                    "retrieved_at": "2026-11-02",
                    "verified": false,
                    "note": "CLAAS firmenweit genannt",
                    "geocoded_by": "nominatim"
                  }
                }
                """);

        Location location = parser.parse(feature);

        assertEquals("res-0", location.externalId);
        assertEquals("Landtechnik Schuster", location.name);
        assertEquals(LocationCategory.MIXED_COMPETITOR, location.category);
        assertEquals(48.6291475, location.latitude);
        assertEquals(16.1016403, location.longitude);
        assertEquals("CLAAS", location.brands);
        assertTrue(location.brandKnown);
        assertEquals("Grund 160", location.street);
        assertEquals("2041", location.postcode);
        assertEquals("Grund", location.city);
        assertEquals("web_research+osm", location.sourceType);
        assertEquals("https://www.landtechnik-schuster.at/", location.sourceUrl);
        assertNull(location.moreSources);
        assertEquals(LocalDate.of(2026, 11, 2), location.retrievedAt);
        assertEquals(Boolean.FALSE, location.verified);
        assertEquals("CLAAS firmenweit genannt", location.note);
        assertEquals("nominatim", location.geocodedBy);
        assertNull(location.id);
    }

    @Test
    void missingGeocodedByIsNull() throws Exception {
        JsonNode feature = mapper.readTree("""
                {"geometry": {"coordinates": [16.0, 48.0]},
                 "properties": {"id": "x", "name": "n", "category": "competitor"}}
                """);

        assertNull(parser.parse(feature).geocodedBy);
    }

    @Test
    void rejectsFeatureWithoutCoordinates() throws Exception {
        JsonNode feature = mapper.readTree("""
                {"geometry": {"type": "Point"},
                 "properties": {"id": "res-x", "name": "n", "category": "competitor"}}
                """);

        InvalidFeatureException e = assertThrows(InvalidFeatureException.class, () -> parser.parse(feature));
        assertTrue(e.getMessage().contains("res-x"));
    }

    @Test
    void rejectsFeatureWithoutGeometry() throws Exception {
        JsonNode feature = mapper.readTree("""
                {"properties": {"id": "res-x", "name": "n", "category": "competitor"}}
                """);

        assertThrows(InvalidFeatureException.class, () -> parser.parse(feature));
    }

    @Test
    void rejectsFeatureWithIncompleteCoordinates() throws Exception {
        JsonNode feature = mapper.readTree("""
                {"geometry": {"coordinates": [16.0]},
                 "properties": {"id": "res-x", "name": "n", "category": "competitor"}}
                """);

        assertThrows(InvalidFeatureException.class, () -> parser.parse(feature));
    }

    @Test
    void rejectsFeatureWithoutCategory() throws Exception {
        JsonNode feature = mapper.readTree("""
                {"geometry": {"coordinates": [16.0, 48.0]},
                 "properties": {"id": "res-x", "name": "n"}}
                """);

        assertThrows(InvalidFeatureException.class, () -> parser.parse(feature));
    }

    @Test
    void rejectsFeatureWithUnknownCategory() throws Exception {
        JsonNode feature = mapper.readTree("""
                {"geometry": {"coordinates": [16.0, 48.0]},
                 "properties": {"id": "res-x", "name": "n", "category": "dealer"}}
                """);

        assertThrows(InvalidFeatureException.class, () -> parser.parse(feature));
    }

    @Test
    void rejectsFeatureWithoutId() throws Exception {
        JsonNode feature = mapper.readTree("""
                {"geometry": {"coordinates": [16.0, 48.0]},
                 "properties": {"name": "n", "category": "competitor"}}
                """);

        InvalidFeatureException e = assertThrows(InvalidFeatureException.class, () -> parser.parse(feature));
        assertTrue(e.getMessage().contains("properties.id"));
    }

    @Test
    void rejectsFeatureWithBlankName() throws Exception {
        JsonNode feature = mapper.readTree("""
                {"geometry": {"coordinates": [16.0, 48.0]},
                 "properties": {"id": "res-x", "name": "  ", "category": "competitor"}}
                """);

        InvalidFeatureException e = assertThrows(InvalidFeatureException.class, () -> parser.parse(feature));
        assertTrue(e.getMessage().contains("name"));
    }

    @Test
    void rejectsFeatureWithInvalidDate() throws Exception {
        JsonNode feature = mapper.readTree("""
                {"geometry": {"coordinates": [16.0, 48.0]},
                 "properties": {"id": "res-x", "name": "n", "category": "competitor",
                                "retrieved_at": "02.11.2026"}}
                """);

        InvalidFeatureException e = assertThrows(InvalidFeatureException.class, () -> parser.parse(feature));
        assertTrue(e.getMessage().contains("retrieved_at"));
    }

    @Test
    void missingOptionalFieldsAreNull() throws Exception {
        JsonNode feature = mapper.readTree("""
                {"geometry": {"coordinates": [16.0, 48.0]},
                 "properties": {"id": "res-x", "name": "n", "category": "john_deere",
                                "verified": null, "retrieved_at": null}}
                """);

        Location location = parser.parse(feature);

        assertEquals(LocationCategory.JOHN_DEERE, location.category);
        assertNull(location.verified);
        assertNull(location.brandKnown);
        assertNull(location.retrievedAt);
        assertNull(location.street);
    }
}
