package de.thi.terramax.location;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * GeoJSON-Geometrie vom Typ {@code Point}. Koordinaten in der Reihenfolge {@code [Länge, Breite]}.
 */
@Schema(name = "Point", description = "GeoJSON-Punkt, Koordinaten als [Länge, Breite]")
public record GeoJsonPoint(@Schema(enumeration = "Point", example = "Point") String type,
        @Schema(description = "[Länge, Breite] in WGS84", example = "[15.410884, 48.183382]") List<Double> coordinates) {

    public static final String GEOJSON_TYPE = "Point";

    public static GeoJsonPoint of(double longitude, double latitude) {
        return new GeoJsonPoint(GEOJSON_TYPE, List.of(longitude, latitude));
    }
}
