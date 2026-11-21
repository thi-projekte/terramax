package de.thi.terramax.tour;

import de.thi.terramax.common.ApiErrors;
import de.thi.terramax.common.ErrorResponse;
import de.thi.terramax.common.TerraMaxApplication;
import de.thi.terramax.customer.Customer;
import de.thi.terramax.dealer.Dealer;
import de.thi.terramax.dealer.DealerEmployee;
import de.thi.terramax.protocol.VisitorProtocol;
import de.thi.terramax.security.CurrentUser;
import de.thi.terramax.security.Roles;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.QuarkusTransactionException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * Besuchstouren. Sichtbarkeit siehe {@link TourAccess}. Die process-engine ruft diese Endpunkte mit dem Token des
 * Nutzers oder mit ihrem Service-Konto ({@code process_engine}) auf; TerraMax ruft die Engine nie auf.
 */
@Path("/tours")
@RolesAllowed({ Roles.SALES_PROFESSIONAL, Roles.DEALER_PRINCIPAL, Roles.PROCESS_ENGINE })
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Touren")
@SecurityRequirement(name = TerraMaxApplication.BEARER_SCHEME)
@APIResponse(responseCode = "401", description = "Kein oder ungültiger Token")
@APIResponse(responseCode = "403", description = "Keine passende Rolle oder (für Nutzer) kein dealer_id im Token", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
public class TourResource {

    private static final Logger LOG = Logger.getLogger(TourResource.class);

    static final String EXAMPLE_TOUR = """
            {"id": 1, "dealerId": "D-100", "tourDate": "2026-11-24", "status": "in_progress",
             "processInstanceId": "8f2c1a7e-1b2c-11f0-9d5e-0242ac120002", "createdAt": "2026-11-24T07:58:12.345+01:00",
             "employeeIds": [1],
             "customers": [
               {"customerId": 3, "externalId": "MOCK-003", "name": "Testbetrieb 03 (fiktiv)", "position": 1,
                "status": "DOKUMENTIERT", "skipReason": null, "startedAt": "2026-11-24T09:15:00+01:00",
                "finishedAt": "2026-11-24T09:55:00+01:00", "latitude": 48.31, "longitude": 15.62},
               {"customerId": 1, "externalId": "MOCK-001", "name": "Testbetrieb 01 (fiktiv)", "position": 2,
                "status": "OFFEN", "skipReason": null, "startedAt": null, "finishedAt": null,
                "latitude": 48.235, "longitude": 16.65}]}""";

    @Inject
    CurrentUser user;

    @Inject
    TourAccess access;

    @GET
    @RolesAllowed({ Roles.SALES_PROFESSIONAL, Roles.DEALER_PRINCIPAL })
    @Operation(summary = "Touren auflisten", description = "dealer_principal: alle Touren des Händlers. "
            + "sales_professional: nur eigene Touren. Neueste zuerst. Nicht für das Service-Konto.")
    @APIResponse(responseCode = "200", description = "Touren", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourSummaryDto.class, type = SchemaType.ARRAY)))
    public List<TourSummaryDto> list() {
        return access.visibleTours().stream().map(TourSummaryDto::from).toList();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Eine Tour abrufen", description = "Mit Halten in Besuchsreihenfolge.")
    @APIResponse(responseCode = "200", description = "Tour", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourDto.class), examples = @ExampleObject(name = "Tour", value = EXAMPLE_TOUR)))
    @APIResponse(responseCode = "404", description = "Unbekannte oder nicht sichtbare Tour", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public TourDto get(@Parameter(description = "ID der Tour", example = "1") @PathParam("id") long id) {
        return TourDto.from(access.visibleTour(id));
    }

    @GET
    @Path("/by-process/{processInstanceId}")
    @Operation(summary = "Tour zu einer Prozessinstanz", description = "Für die process-engine: findet die Tour über "
            + "die processInstanceId, z. B. wenn die tourId im Prozesskontext fehlt.")
    @APIResponse(responseCode = "200", description = "Tour", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourDto.class), examples = @ExampleObject(name = "Tour", value = EXAMPLE_TOUR)))
    @APIResponse(responseCode = "404", description = "Keine (sichtbare) Tour zu dieser Prozessinstanz", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public TourDto byProcess(
            @Parameter(description = "Prozessinstanz in CIB Seven", example = "8f2c1a7e-1b2c-11f0-9d5e-0242ac120002") @PathParam("processInstanceId") String processInstanceId) {
        return TourDto.from(access.visibleTourByProcess(processInstanceId));
    }

    @GET
    @Path("/{id}/status")
    @Operation(summary = "Kompakter Stand für Gateways", description = "Für Gateways der process-engine, z. B. "
            + "\"Mindestens zwei Kunden?\" (anzahlHalte >= 2) oder \"Alle Kunden besucht?\" (alleErledigt).")
    @APIResponse(responseCode = "200", description = "Stand", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourProcessStatusDto.class), examples = {
            @ExampleObject(name = "Tour läuft", value = """
                    {"tourId": 1, "processInstanceId": "8f2c1a7e-1b2c-11f0-9d5e-0242ac120002", "tourStatus": "in_progress",
                     "anzahlHalte": 5, "offeneHalte": 3, "dokumentiert": 1, "uebersprungen": 1, "alleErledigt": false,
                     "naechsterHalt": {"kundeId": 7, "position": 3, "name": "Testbetrieb 07 (fiktiv)", "begonnen": false}}"""),
            @ExampleObject(name = "Alle erledigt", value = """
                    {"tourId": 1, "processInstanceId": "8f2c1a7e-1b2c-11f0-9d5e-0242ac120002", "tourStatus": "in_progress",
                     "anzahlHalte": 5, "offeneHalte": 0, "dokumentiert": 4, "uebersprungen": 1, "alleErledigt": true,
                     "naechsterHalt": null}""") }))
    @APIResponse(responseCode = "404", description = "Unbekannte oder nicht sichtbare Tour", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public TourProcessStatusDto status(
            @Parameter(description = "ID der Tour", example = "1") @PathParam("id") long id) {
        return TourProcessStatusDto.from(access.visibleTour(id));
    }

    @GET
    @Path("/{id}/overview")
    @Operation(summary = "Tourübersicht", description = "Alle Halte mit Status, Grund, Zeiten und Protokoll-IDs. "
            + "Ersetzt die Prozessvariable touruebersicht (Service Task \"Tourübersicht erstellen\").")
    @APIResponse(responseCode = "200", description = "Übersicht", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourOverviewDto.class), examples = @ExampleObject(name = "Abgeschlossene Tour", value = """
            {"tourId": 1, "processInstanceId": "8f2c1a7e-1b2c-11f0-9d5e-0242ac120002", "tourDatum": "2026-11-24",
             "tourStatus": "completed",
             "besuche": [
               {"position": 1, "kundeId": 3, "externalId": "MOCK-003", "name": "Testbetrieb 03 (fiktiv)", "ort": "Mostviertel",
                "status": "DOKUMENTIERT", "grund": null, "besuchBegonnenAm": "2026-11-24T09:15:00+01:00",
                "besuchAbgeschlossenAm": "2026-11-24T09:55:00+01:00", "protokollIds": [7]},
               {"position": 2, "kundeId": 1, "externalId": "MOCK-001", "name": "Testbetrieb 01 (fiktiv)", "ort": "Marchfeld",
                "status": "UEBERSPRUNGEN", "grund": "Betrieb nicht erreichbar", "besuchBegonnenAm": null,
                "besuchAbgeschlossenAm": "2026-11-24T10:30:00+01:00", "protokollIds": []},
               {"position": 3, "kundeId": 2, "externalId": "MOCK-002", "name": "Testbetrieb 02 (fiktiv)", "ort": "Weinviertel",
                "status": "OFFEN", "grund": null, "besuchBegonnenAm": null, "besuchAbgeschlossenAm": null, "protokollIds": []}],
             "anzahlDokumentiert": 1, "anzahlUebersprungen": 1, "anzahlOffen": 1}""")))
    @APIResponse(responseCode = "404", description = "Unbekannte oder nicht sichtbare Tour", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public TourOverviewDto overview(@Parameter(description = "ID der Tour", example = "1") @PathParam("id") long id) {
        Tour tour = access.visibleTour(id);
        Map<Long, List<Long>> protocolIds = VisitorProtocol.listByTour(tour.id).stream()
                .sorted((a, b) -> Long.compare(a.id, b.id))
                .collect(Collectors.groupingBy(p -> p.customer.id, Collectors.mapping(p -> p.id, Collectors.toList())));
        return TourOverviewDto.from(tour, protocolIds);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Tour anlegen", description = "Legt eine Tour im Status planned an, alle Halte OFFEN. "
            + "Ein angemeldeter Nutzer wird als Fahrer eingetragen; ein sales_professional braucht dafür einen "
            + "Mitarbeiter-Eintrag. Mit processInstanceId idempotent: Gibt es zur Prozessinstanz schon eine Tour, "
            + "antwortet der Endpunkt mit 200 und dieser Tour, ohne etwas zu ändern. Das Service-Konto muss dealerId "
            + "angeben.")
    @RequestBody(required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourRequests.Create.class), examples = {
            @ExampleObject(name = "Engine mit Nutzer-Token", description = "Nach \"Besuchsplan erzeugen\": Kunden aus GET /tours/plan", value = """
                    {"tourDate": "2026-11-24", "customerIds": [3, 1, 2],
                     "processInstanceId": "8f2c1a7e-1b2c-11f0-9d5e-0242ac120002"}"""),
            @ExampleObject(name = "Engine mit Service-Konto", value = """
                    {"tourDate": "2026-11-24", "customerIds": [3, 1, 2], "employeeIds": [1], "dealerId": "D-100",
                     "processInstanceId": "8f2c1a7e-1b2c-11f0-9d5e-0242ac120002"}"""),
            @ExampleObject(name = "Ohne Prozess (SPA)", value = """
                    {"tourDate": "2026-11-24", "customerIds": [3, 1]}""") }))
    @APIResponse(responseCode = "201", description = "Angelegt", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourDto.class)))
    @APIResponse(responseCode = "200", description = "Zur processInstanceId gab es schon eine Tour; sie kommt unverändert zurück", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourDto.class), examples = @ExampleObject(name = "Bestehende Tour", value = EXAMPLE_TOUR)))
    @APIResponse(responseCode = "400", description = "Ungültige Angaben, z. B. Kunde eines anderen Händlers oder dealerId fehlt beim Service-Konto", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "409", description = "Die processInstanceId gehört zu einer Tour, die der Aufrufer nicht sieht", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public Response create(TourRequests.Create request, @Context UriInfo uriInfo) {
        access.requireCaller();
        if (request == null || request.tourDate() == null) {
            throw ApiErrors.badRequest("Das Datum (tourDate) fehlt.");
        }
        String processInstanceId = blankToNull(request.processInstanceId());
        if (processInstanceId != null && processInstanceId.length() > TourRequests.MAX_PROCESS_INSTANCE_ID_LENGTH) {
            throw ApiErrors.badRequest("Die processInstanceId ist zu lang.");
        }
        if (processInstanceId != null) {
            Optional<Response> existing = existingTour(processInstanceId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        try {
            TourDto created = QuarkusTransaction.requiringNew().call(() -> persistNewTour(request, processInstanceId));
            URI location = uriInfo.getBaseUriBuilder().path("tours").path(String.valueOf(created.id())).build();
            return Response.created(location).entity(created).build();
        } catch (PersistenceException | QuarkusTransactionException e) {
            // Zwei gleichzeitige Aufrufe mit derselben processInstanceId: Der andere war schneller (Unique-Index)
            if (processInstanceId != null) {
                Optional<Response> existing = existingTour(processInstanceId);
                if (existing.isPresent()) {
                    LOG.debugf("Tour zur Prozessinstanz %s wurde parallel angelegt", processInstanceId);
                    return existing.get();
                }
            }
            throw e;
        }
    }

    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Tour ändern", description = "Status der Tour (z. B. completed bei \"Tour abschließen\", "
            + "discarded bei \"Tour verworfen\") und Reihenfolge der Halte (\"Besuchsplan prüfen\"). Den Stand "
            + "einzelner Halte ändern die Endpunkte unter /tours/{id}/stops/{customerId}.")
    @RequestBody(required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourRequests.Patch.class), examples = {
            @ExampleObject(name = "Tour abschließen", value = "{\"status\": \"completed\"}"),
            @ExampleObject(name = "Tour verworfen", value = "{\"status\": \"discarded\"}"),
            @ExampleObject(name = "Reihenfolge ändern", value = "{\"customerOrder\": [2, 3, 1]}") }))
    @APIResponse(responseCode = "200", description = "Geändert", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourDto.class)))
    @APIResponse(responseCode = "400", description = "Ungültige Angaben", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Unbekannte oder nicht sichtbare Tour", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public TourDto patch(@Parameter(description = "ID der Tour", example = "1") @PathParam("id") long id,
            TourRequests.Patch request) {
        if (request == null) {
            throw ApiErrors.badRequest("Die Angaben fehlen.");
        }
        Tour tour = access.visibleTour(id);
        Map<Long, TourCustomer> stops = tour.stops.stream()
                .collect(Collectors.toMap(s -> s.customer.id, Function.identity()));

        if (request.status() != null) {
            try {
                tour.status = TourStatus.fromDbValue(request.status());
            } catch (IllegalArgumentException e) {
                throw ApiErrors.badRequest(e.getMessage());
            }
        }
        if (request.customerOrder() != null) {
            requireDistinct(request.customerOrder());
            if (!new HashSet<>(request.customerOrder()).equals(stops.keySet())) {
                throw ApiErrors.badRequest("customerOrder muss alle Kunden der Tour genau einmal enthalten.");
            }
            int position = 1;
            for (Long customerId : request.customerOrder()) {
                stops.get(customerId).position = position++;
            }
            tour.stops.sort((a, b) -> Integer.compare(a.position, b.position));
        }
        return TourDto.from(tour);
    }

    /** Antwort 200 mit der bestehenden Tour zur Prozessinstanz, oder 409, wenn der Aufrufer sie nicht sieht. */
    private Optional<Response> existingTour(String processInstanceId) {
        return Tour.findByProcessInstanceId(processInstanceId).map(tour -> {
            if (!access.isVisible(tour)) {
                throw ApiErrors.conflict("Die processInstanceId ist bereits einer anderen Tour zugeordnet.");
            }
            return Response.ok(TourDto.from(tour)).build();
        });
    }

    private TourDto persistNewTour(TourRequests.Create request, String processInstanceId) {
        String dealerId = dealerIdFor(request);
        Optional<DealerEmployee> self = user.isServiceAccount() ? Optional.empty() : user.employee();
        if (self.isEmpty() && !user.isDealerPrincipal() && !user.isServiceAccount()) {
            throw ApiErrors.forbidden("Für diesen Nutzer gibt es keinen Mitarbeiter-Eintrag beim Händler.");
        }
        Dealer dealer = Dealer.findById(dealerId);
        if (dealer == null) {
            throw user.isServiceAccount() ? ApiErrors.badRequest("Unbekannter Händler: " + dealerId)
                    : ApiErrors.notFound("Unbekannter Händler: " + dealerId);
        }

        Tour tour = new Tour();
        tour.dealer = dealer;
        tour.tourDate = request.tourDate();
        tour.processInstanceId = processInstanceId;
        self.ifPresent(tour.employees::add);
        for (Long employeeId : nullToEmpty(request.employeeIds())) {
            DealerEmployee employee = employeeId == null ? null : DealerEmployee.findById(employeeId);
            if (employee == null || !employee.dealer.id.equals(dealerId)) {
                throw ApiErrors.badRequest("Unbekannter Mitarbeiter: " + employeeId);
            }
            tour.employees.add(employee);
        }
        List<Long> customerIds = nullToEmpty(request.customerIds());
        requireDistinct(customerIds);
        int position = 1;
        for (Long customerId : customerIds) {
            Customer customer = Customer.findForDealer(customerId, dealerId)
                    .orElseThrow(() -> ApiErrors.badRequest("Unbekannter Kunde: " + customerId));
            tour.stops.add(TourCustomer.of(tour, customer, position++));
        }
        tour.persist();
        tour.flush();
        LOG.infof("Tour %d für Händler %s angelegt (Prozessinstanz %s)", tour.id, dealerId, processInstanceId);
        return TourDto.from(tour);
    }

    /** Nutzer: Händler aus dem Token. Service-Konto: Händler aus dem Request (Pflicht). */
    private String dealerIdFor(TourRequests.Create request) {
        String requested = blankToNull(request.dealerId());
        if (user.isServiceAccount()) {
            if (requested == null) {
                throw ApiErrors.badRequest("Das Service-Konto muss dealerId angeben.");
            }
            return requested;
        }
        String own = user.requireDealerId();
        if (requested != null && !requested.equals(own)) {
            throw ApiErrors.forbidden("dealerId passt nicht zum Händler im Token.");
        }
        return own;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static void requireDistinct(List<Long> ids) {
        Set<Long> seen = new HashSet<>();
        for (Long id : ids) {
            if (id == null || !seen.add(id)) {
                throw ApiErrors.badRequest("Kunden-IDs müssen eindeutig sein: " + id);
            }
        }
    }
}
