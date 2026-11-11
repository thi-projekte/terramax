package de.thi.terramax.location;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * GeoJSON-FeatureCollection mit Standorten.
 */
@Schema(name = "LocationFeatureCollection", description = "Standorte als GeoJSON-FeatureCollection")
public record GeoJsonFeatureCollection(
        @Schema(enumeration = "FeatureCollection", example = "FeatureCollection") String type,
        List<GeoJsonFeature> features) {

    public static final String GEOJSON_TYPE = "FeatureCollection";

    public static GeoJsonFeatureCollection of(List<GeoJsonFeature> features) {
        return new GeoJsonFeatureCollection(GEOJSON_TYPE, List.copyOf(features));
    }
}
