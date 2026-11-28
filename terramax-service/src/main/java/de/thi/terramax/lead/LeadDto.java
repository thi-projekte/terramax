package de.thi.terramax.lead;

import de.thi.terramax.customer.Customer;
import de.thi.terramax.customer.CustomerSource;
import de.thi.terramax.customer.CustomerStatus;
import java.time.OffsetDateTime;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Ein erfasster Lead: der Kunde, dazu der Ansprechpartner und der Kommentar der (neuesten) Erfassung.
 */
@Schema(name = "Lead", description = "Erfasster Lead mit Ansprechpartner und Kommentar")
public record LeadDto(@Schema(description = "ID des Kunden", example = "7") Long customerId,
        @Schema(description = "true, wenn bei der Erfassung ein neuer Kunde entstanden ist") boolean newCustomer,
        @Schema(example = "LEAD") CustomerStatus status, @Schema(example = "Hof Maier GbR") String name,
        @Schema(example = "MESSE") CustomerSource source, String street, String postalCode, String city,
        Double latitude, Double longitude, ContactDto contact, NoteDto note) {

    @Schema(name = "LeadContactDto", description = "Ansprechpartner")
    public record ContactDto(Long id, String name, String role, String phone, String email) {

        static ContactDto from(CustomerContact c) {
            return c == null ? null : new ContactDto(c.id, c.name, c.role, c.phone, c.email);
        }
    }

    @Schema(name = "LeadNoteDto", description = "Kommentar zur Erfassung")
    public record NoteDto(Long id, String comment, String transcript, String eventName, CustomerSource source,
            @Schema(description = "Claim sub des Erstellers") String createdBy, OffsetDateTime createdAt) {

        static NoteDto from(LeadNote n) {
            return n == null ? null
                    : new NoteDto(n.id, n.comment, n.transcript, n.eventName, n.source, n.createdBy, n.createdAt);
        }
    }

    static LeadDto of(Customer customer, boolean newCustomer, LeadNote note) {
        CustomerContact contact = note == null ? null : note.contact;
        return new LeadDto(customer.id, newCustomer, customer.status, customer.name, customer.source,
                customer.street, customer.postalCode, customer.city, customer.latitude, customer.longitude,
                ContactDto.from(contact), NoteDto.from(note));
    }
}
