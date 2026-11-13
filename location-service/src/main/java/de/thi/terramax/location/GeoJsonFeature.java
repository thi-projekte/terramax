package de.thi.terramax.location;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * GeoJSON-Feature eines Standorts: Punkt-Geometrie plus Eigenschaften laut API-Vertrag.
 */
@Schema(name = "LocationFeature", description = "Ein Standort als GeoJSON-Feature")
public record GeoJsonFeature(@Schema(enumeration = "Feature", example = "Feature") String type, GeoJsonPoint geometry,
        LocationProperties properties) {

    public static final String GEOJSON_TYPE = "Feature";

    public static GeoJsonFeature of(GeoJsonPoint geometry, LocationProperties properties) {
        return new GeoJsonFeature(GEOJSON_TYPE, geometry, properties);
    }
}
