package de.thi.terramax.dealer;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Händler in der API.
 */
@Schema(name = "Dealer", description = "Händler")
public record DealerDto(@Schema(description = "ID, entspricht dem Claim dealer_id", example = "D-100") String id,
        @Schema(example = "Landtechnik Wels GmbH") String name, String street, String postcode, String city,
        Double latitude, Double longitude,
        @Schema(description = "ID des Standorts auf der Marktkarte (properties.id), falls verknüpft", example = "jd-3") String locationId) {

    public static DealerDto from(Dealer dealer) {
        return new DealerDto(dealer.id, dealer.name, dealer.street, dealer.postcode, dealer.city, dealer.latitude,
                dealer.longitude, dealer.location == null ? null : dealer.location.externalId);
    }
}
