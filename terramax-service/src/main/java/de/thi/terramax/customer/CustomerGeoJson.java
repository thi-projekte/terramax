package de.thi.terramax.customer;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.thi.terramax.location.GeoJsonPoint;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Kunden als GeoJSON. Die Eigenschaften folgen {@code frontend/public/kunden.geojson}, damit das Frontend die Datei
 * später durch diesen Endpunkt ersetzen kann. Kunden ohne Koordinaten fehlen in der FeatureCollection.
 */
public final class CustomerGeoJson {

    private CustomerGeoJson() {
    }

    @Schema(name = "CustomerFeatureCollection", description = "Kunden als GeoJSON-FeatureCollection")
    public record FeatureCollection(@Schema(enumeration = "FeatureCollection") String type,
            List<Feature> features) {
    }

    @Schema(name = "CustomerFeature", description = "Ein Kunde als GeoJSON-Feature")
    public record Feature(@Schema(enumeration = "Feature") String type, GeoJsonPoint geometry,
            Properties properties) {
    }

    @Schema(name = "CustomerProperties", description = "Eigenschaften eines Kunden")
    public record Properties(@Schema(description = "ID im TerraMax-Service", example = "1") @JsonProperty("id") Long id,
            @Schema(description = "Kundennummer im CRM", example = "CRM-4711") @JsonProperty("customer_id") String customerId,
            @JsonProperty("name") String name, @JsonProperty("region") String region,
            @JsonProperty("segment") String segment, @JsonProperty("crm_url") String crmUrl) {
    }

    public static FeatureCollection of(List<Customer> customers) {
        return new FeatureCollection("FeatureCollection",
                customers.stream().filter(c -> c.latitude != null && c.longitude != null)
                        .map(c -> new Feature("Feature", GeoJsonPoint.of(c.longitude, c.latitude),
                                new Properties(c.id, c.externalId, c.name, c.region, c.segment, c.crmUrl)))
                        .toList());
    }
}
