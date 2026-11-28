package de.thi.terramax.lead;

import de.thi.terramax.common.ApiErrors;
import de.thi.terramax.common.ErrorResponse;
import de.thi.terramax.common.TerraMaxApplication;
import de.thi.terramax.customer.Customer;
import de.thi.terramax.customer.CustomerStatus;
import de.thi.terramax.dealer.Dealer;
import de.thi.terramax.security.CurrentUser;
import de.thi.terramax.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * Schnellerfassung von Leads (#134), z. B. per Visitenkarte auf einer Messe. Ein Lead ist ein Kunde mit Status
 * {@code LEAD}; Kundenliste, Karte und Tourplanung zeigen ihn vorerst nicht (#126). Kontaktdaten und Kommentare werden
 * nicht geloggt.
 */
@Path("/leads")
@RolesAllowed({ Roles.SALES_PROFESSIONAL, Roles.DEALER_PRINCIPAL })
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Leads")
@SecurityRequirement(name = TerraMaxApplication.BEARER_SCHEME)
@APIResponse(responseCode = "401", description = "Kein oder ungültiger Token")
@APIResponse(responseCode = "403", description = "Keine TerraMax-Rolle oder kein dealer_id im Token", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
public class LeadResource {

    private static final Logger LOG = Logger.getLogger(LeadResource.class);

    @Inject
    CurrentUser user;

    @GET
    @Operation(summary = "Leads auflisten", description = "Leads des eigenen Händlers, zuletzt erfasste zuerst, "
            + "jeweils mit Ansprechpartner und Kommentar der neuesten Erfassung.")
    @APIResponse(responseCode = "200", description = "Leads", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = LeadDto.class, type = SchemaType.ARRAY)))
    public List<LeadDto> list() {
        List<Customer> leads = Customer.listLeadsByDealer(user.requireDealerId());
        Map<Long, LeadNote> latest = new LinkedHashMap<>();
        for (LeadNote note : LeadNote.listForCustomers(leads.stream().map(c -> c.id).toList())) {
            latest.putIfAbsent(note.customer.id, note);
        }
        return leads.stream().map(c -> LeadDto.of(c, false, latest.get(c.id))).toList();
    }

    /**
     * Aufrufer ist der speech-service ({@code TerraMaxApi.createLead}). Er reicht den {@code Authorization}-Header des
     * Nutzers unverändert durch, {@code createdBy} ist also dessen {@code sub}.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Lead erfassen", description = "Ohne existingCustomerId entsteht ein neuer Kunde mit Status "
            + "LEAD (Name = Firma, sonst Name der Person). Mit existingCustomerId werden Ansprechpartner und Kommentar "
            + "an diesen Kunden gehängt; fehlende Adresse und Koordinaten werden ergänzt, der Status bleibt.")
    @RequestBody(required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = CreateLeadRequest.class), examples = @ExampleObject(name = "Visitenkarte", value = """
            {"source": "MESSE", "eventName": "Agraria Wels 2026",
             "contact": {"name": "Josef Maier", "company": "Hof Maier GbR", "role": "Betriebsleiter",
                         "phone": "+43 664 1234567", "email": "maier@example.at",
                         "street": "Hauptstraße 1", "postalCode": "4600", "city": "Wels"},
             "latitude": 48.16, "longitude": 14.03,
             "comment": "Interesse an 8R 410, Rückruf nach der Ernte."}""")))
    @APIResponse(responseCode = "201", description = "Erfasst", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = LeadDto.class)))
    @APIResponse(responseCode = "400", description = "Ungültige Angaben", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Händler noch nicht angelegt oder existingCustomerId unbekannt", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response create(CreateLeadRequest request) {
        String dealerId = user.requireDealerId();
        String createdBy = user.requireSubject();
        if (request == null) {
            throw ApiErrors.badRequest("Die Angaben fehlen.");
        }
        request.validate();
        CreateLeadRequest.Contact data = request.contact();

        boolean newCustomer = request.existingCustomerId() == null;
        Customer customer;
        if (newCustomer) {
            customer = new Customer();
            customer.dealer = Dealer.<Dealer> findByIdOptional(dealerId)
                    .orElseThrow(() -> ApiErrors.notFound("Unbekannter Händler: " + dealerId));
            customer.name = request.customerName();
            customer.status = CustomerStatus.LEAD;
            customer.source = request.sourceOrDefault();
            customer.street = CreateLeadRequest.blankToNull(data.street());
            customer.postalCode = CreateLeadRequest.blankToNull(data.postalCode());
            customer.city = CreateLeadRequest.blankToNull(data.city());
            customer.latitude = request.latitude();
            customer.longitude = request.longitude();
            customer.persist();
        } else {
            customer = Customer.findForDealer(request.existingCustomerId(), dealerId)
                    .orElseThrow(() -> ApiErrors.notFound("Unbekannter Kunde: " + request.existingCustomerId()));
            fillMissing(customer, request);
        }

        CustomerContact contact = null;
        if (CreateLeadRequest.blankToNull(data.name()) != null) {
            contact = new CustomerContact();
            contact.customer = customer;
            contact.name = data.name().strip();
            contact.role = CreateLeadRequest.blankToNull(data.role());
            contact.phone = CreateLeadRequest.blankToNull(data.phone());
            contact.email = CreateLeadRequest.blankToNull(data.email());
            contact.persist();
        }

        LeadNote note = new LeadNote();
        note.customer = customer;
        note.contact = contact;
        note.comment = CreateLeadRequest.blankToNull(request.comment());
        note.transcript = CreateLeadRequest.blankToNull(request.transcript());
        note.eventName = CreateLeadRequest.blankToNull(request.eventName());
        note.source = request.sourceOrDefault();
        note.createdBy = createdBy;
        note.persist();

        LOG.infof("Lead für Kunde %d erfasst (neuer Kunde: %s)", customer.id, newCustomer);
        return Response.status(Response.Status.CREATED).entity(LeadDto.of(customer, newCustomer, note)).build();
    }

    /** Ergänzt beim bestehenden Kunden nur, was noch fehlt; vorhandene Daten bleiben unverändert. */
    private static void fillMissing(Customer customer, CreateLeadRequest request) {
        CreateLeadRequest.Contact data = request.contact();
        if (customer.street == null && customer.postalCode == null && customer.city == null) {
            customer.street = CreateLeadRequest.blankToNull(data.street());
            customer.postalCode = CreateLeadRequest.blankToNull(data.postalCode());
            customer.city = CreateLeadRequest.blankToNull(data.city());
        }
        if (customer.latitude == null && customer.longitude == null) {
            customer.latitude = request.latitude();
            customer.longitude = request.longitude();
        }
    }
}
