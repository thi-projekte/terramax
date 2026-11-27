package de.thi.terramax.lead;

import de.thi.terramax.common.ApiErrors;
import de.thi.terramax.customer.CustomerSource;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Schnellerfassung eines Leads, z. B. aus einer Visitenkarte (#134). Aufrufer ist der speech-service
 * ({@code TerraMaxApi.createLead}) mit dem Token des Nutzers. Ohne {@code existingCustomerId} entsteht ein neuer Kunde
 * mit Status {@code LEAD}; mit ihr werden Ansprechpartner und Kommentar an den bestehenden Kunden gehängt.
 */
@Schema(name = "CreateLeadRequest", description = "Lead schnell erfassen (z. B. Visitenkarte auf einer Messe)")
public record CreateLeadRequest(
        @Schema(description = "Herkunft, Standard MESSE", example = "MESSE") CustomerSource source,
        @Schema(description = "z. B. Name der Messe", example = "Agraria Wels 2026") String eventName,
        @Schema(required = true) Contact contact,
        @Schema(description = "Breite in WGS84, nur zusammen mit longitude", example = "48.16") Double latitude,
        @Schema(description = "Länge in WGS84, nur zusammen mit latitude", example = "14.03") Double longitude,
        @Schema(description = "Kurzer Kommentar", example = "Interesse an 8R 410, Rückruf nach der Ernte.") String comment,
        @Schema(description = "Transkript der Sprachnotiz, optional") String transcript,
        @Schema(description = "Bestehender Kunde des eigenen Händlers, optional", example = "3") Long existingCustomerId) {

    static final int MAX_COMMENT_LENGTH = 2_000;
    /** Wie bei Besuchsprotokollen (CreateProtocolRequest). */
    static final int MAX_TRANSCRIPT_LENGTH = 20_000;

    @Schema(name = "LeadContact", description = "Daten von der Visitenkarte")
    public record Contact(@Schema(example = "Josef Maier") String name,
            @Schema(example = "Hof Maier GbR") String company, @Schema(example = "Betriebsleiter") String role,
            @Schema(example = "+43 664 1234567") String phone, @Schema(example = "maier@example.at") String email,
            @Schema(example = "Hauptstraße 1") String street, @Schema(example = "4600") String postalCode,
            @Schema(example = "Wels") String city) {
    }

    /**
     * @throws jakarta.ws.rs.BadRequestException
     *             bei fehlendem Namen, zu langen Angaben oder ungültigen Koordinaten
     */
    void validate() {
        if (contact == null || (isBlank(contact.name()) && isBlank(contact.company()))) {
            throw ApiErrors.badRequest("Name oder Firma fehlt.");
        }
        maxLength(contact.name(), 255, "Der Name");
        maxLength(contact.company(), 255, "Die Firma");
        maxLength(contact.role(), 255, "Die Funktion");
        maxLength(contact.phone(), 64, "Die Telefonnummer");
        maxLength(contact.email(), 255, "Die E-Mail-Adresse");
        maxLength(contact.street(), 255, "Die Straße");
        maxLength(contact.postalCode(), 16, "Die Postleitzahl");
        maxLength(contact.city(), 255, "Der Ort");
        maxLength(eventName, 255, "Der Name der Veranstaltung");
        maxLength(comment, MAX_COMMENT_LENGTH, "Der Kommentar");
        maxLength(transcript, MAX_TRANSCRIPT_LENGTH, "Das Transkript");
        if ((latitude == null) != (longitude == null)) {
            throw ApiErrors.badRequest("latitude und longitude nur gemeinsam angeben.");
        }
        if (latitude != null && (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)) {
            throw ApiErrors.badRequest("Koordinaten außerhalb des gültigen Bereichs.");
        }
    }

    /** Herkunft, ohne Angabe {@link CustomerSource#MESSE}. */
    CustomerSource sourceOrDefault() {
        return source == null ? CustomerSource.MESSE : source;
    }

    /** Name des neuen Kunden: Firma, sonst der Name der Person. */
    String customerName() {
        return isBlank(contact.company()) ? contact.name().strip() : contact.company().strip();
    }

    static String blankToNull(String value) {
        return isBlank(value) ? null : value.strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void maxLength(String value, int max, String label) {
        if (value != null && value.strip().length() > max) {
            throw ApiErrors.badRequest(label + " ist zu lang (max. " + max + " Zeichen).");
        }
    }
}
