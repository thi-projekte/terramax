package de.thi.terramax.location;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * Bildet {@link Location}-Entities auf GeoJSON-DTOs ab. Koordinaten in der GeoJSON-Reihenfolge {@code [Länge, Breite]}.
 */
@ApplicationScoped
public class LocationGeoJsonMapper {

    public GeoJsonFeatureCollection toFeatureCollection(List<Location> locations) {
        return GeoJsonFeatureCollection.of(locations.stream().map(this::toFeature).toList());
    }

    public GeoJsonFeature toFeature(Location location) {
        return GeoJsonFeature.of(GeoJsonPoint.of(location.longitude, location.latitude), toProperties(location));
    }

    LocationProperties toProperties(Location location) {
        return new LocationProperties(location.externalId, location.name,
                location.category == null ? null : location.category.getDbValue(), location.brands,
                location.brandKnown, location.street, location.postcode, location.city, location.sourceType,
                location.sourceUrl, location.moreSources,
                location.retrievedAt == null ? null : location.retrievedAt.toString(), location.verified,
                location.note);
    }
}
