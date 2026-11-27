package de.thi.terramax.customer;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Kunde in der API.
 */
@Schema(name = "Customer", description = "Kunde eines Händlers")
public record CustomerDto(@Schema(example = "1") Long id,
        @Schema(description = "Kundennummer im CRM", example = "CRM-4711") String externalId,
        @Schema(example = "Hof Huber") String name, @Schema(example = "Marchfeld") String region,
        @Schema(example = "Ackerbau") String segment, @Schema(example = "48.235") Double latitude,
        @Schema(example = "16.65") Double longitude, @Schema(description = "Link ins CRM") String crmUrl,
        @Schema(description = "Bestandskunde oder Lead", example = "CUSTOMER") CustomerStatus status,
        @Schema(example = "Hauptstraße 1") String street, @Schema(example = "3100") String postalCode,
        @Schema(example = "St. Pölten") String city) {

    public static CustomerDto from(Customer c) {
        return new CustomerDto(c.id, c.externalId, c.name, c.region, c.segment, c.latitude, c.longitude, c.crmUrl,
                c.status, c.street, c.postalCode, c.city);
    }
}
