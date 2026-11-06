package de.thi.terramax.location;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Wandelt ein einzelnes GeoJSON-Feature (Point) in eine {@link Location} um. GeoJSON-Koordinaten haben die Reihenfolge
 * {@code [longitude, latitude]}.
 */
@ApplicationScoped
public class GeoJsonFeatureParser {

    /**
     * @param feature
     *            ein Element aus {@code features} einer FeatureCollection
     *
     * @return die (noch nicht gespeicherte) Location
     *
     * @throws InvalidFeatureException
     *             wenn ID, Name, Kategorie oder Koordinaten fehlen bzw. ungültig sind
     */
    public Location parse(JsonNode feature) throws InvalidFeatureException {
        JsonNode props = feature.path("properties");
        String externalId = text(props, "id");
        if (externalId == null) {
            throw new InvalidFeatureException("Feature ohne properties.id");
        }

        String name = text(props, "name");
        if (name == null) {
            throw new InvalidFeatureException("Feature " + externalId + ": name fehlt");
        }

        String categoryValue = text(props, "category");
        if (categoryValue == null) {
            throw new InvalidFeatureException("Feature " + externalId + ": category fehlt");
        }
        LocationCategory category;
        try {
            category = LocationCategory.fromDbValue(categoryValue);
        } catch (IllegalArgumentException e) {
            throw new InvalidFeatureException("Feature " + externalId + ": " + e.getMessage());
        }

        JsonNode coordinates = feature.path("geometry").path("coordinates");
        if (!coordinates.isArray() || coordinates.size() < 2 || !coordinates.get(0).isNumber()
                || !coordinates.get(1).isNumber()) {
            throw new InvalidFeatureException("Feature " + externalId + ": Koordinaten fehlen oder sind ungültig");
        }

        Location location = new Location();
        location.externalId = externalId;
        location.name = name;
        location.category = category;
        // GeoJSON: [lon, lat]
        location.longitude = coordinates.get(0).asDouble();
        location.latitude = coordinates.get(1).asDouble();
        location.brands = text(props, "brands");
        location.brandKnown = bool(props, "brand_known");
        location.street = text(props, "street");
        location.postcode = text(props, "postcode");
        location.city = text(props, "city");
        location.sourceType = text(props, "source_type");
        location.sourceUrl = text(props, "source_url");
        location.moreSources = text(props, "more_sources");
        location.retrievedAt = date(props, "retrieved_at", externalId);
        location.verified = bool(props, "verified");
        location.note = text(props, "note");
        location.geocodedBy = text(props, "geocoded_by");
        return location;
    }

    /** Liefert den Text des Feldes oder {@code null}, wenn es fehlt, null oder leer ist. */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().strip();
        return text.isEmpty() ? null : text;
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    private static LocalDate date(JsonNode node, String field, String externalId) throws InvalidFeatureException {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidFeatureException("Feature " + externalId + ": ungültiges Datum " + field + "=" + value);
        }
    }
}
