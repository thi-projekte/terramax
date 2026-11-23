package de.thi.terramax.customer;

import de.thi.terramax.common.ApiErrors;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Anlegen oder vollständiges Ändern eines Kunden. Der Händler kommt immer aus dem Token.
 */
@Schema(name = "CustomerRequest", description = "Kunde anlegen oder ändern")
public record CustomerRequest(
        @Schema(description = "Kundennummer im CRM, eindeutig", example = "CRM-4711") String externalId,
        @Schema(required = true, example = "Hof Huber") String name, @Schema(example = "Marchfeld") String region,
        @Schema(example = "Ackerbau") String segment,
        @Schema(description = "Breite in WGS84, nur zusammen mit longitude", example = "48.235") Double latitude,
        @Schema(description = "Länge in WGS84, nur zusammen mit latitude", example = "16.65") Double longitude,
        @Schema(description = "Link ins CRM") String crmUrl) {

    /**
     * @throws jakarta.ws.rs.BadRequestException
     *             bei fehlendem Namen oder ungültigen Koordinaten
     */
    void validate() {
        if (name == null || name.isBlank()) {
            throw ApiErrors.badRequest("Der Name fehlt.");
        }
        if ((latitude == null) != (longitude == null)) {
            throw ApiErrors.badRequest("latitude und longitude nur gemeinsam angeben.");
        }
        if (latitude != null && (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)) {
            throw ApiErrors.badRequest("Koordinaten außerhalb des gültigen Bereichs.");
        }
    }

    void applyTo(Customer customer) {
        customer.externalId = blankToNull(externalId);
        customer.name = name.strip();
        customer.region = blankToNull(region);
        customer.segment = blankToNull(segment);
        customer.latitude = latitude;
        customer.longitude = longitude;
        customer.crmUrl = blankToNull(crmUrl);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
