package de.thi.terramax.tour;

import de.thi.terramax.ai.VisitPrioritizer.PlanningConstraints;
import de.thi.terramax.common.ApiErrors;
import de.thi.terramax.common.ErrorResponse;
import de.thi.terramax.common.TerraMaxApplication;
import de.thi.terramax.customer.Customer;
import de.thi.terramax.security.CurrentUser;
import de.thi.terramax.security.Roles;
import de.thi.terramax.tour.TourPlanner.ProposedStop;
import de.thi.terramax.tour.TourPlanner.TourPlanProposal;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Besuchsplan für einen Tag, übernommen aus dem tour-service (PR #53) und auf die Kunden aus der Datenbank umgestellt.
 * Gleicher Pfad, gleiche Parameter und gleiches Antwortformat ({@link TourPlanDto}), damit die process-engine nur
 * {@code TOUR_SERVICE_URL} umstellen muss. Wie bisher nur mit Rolle {@code sales_professional}; der Händler kommt aus
 * dem Token ({@code dealer_id}).
 *
 * <pre>
 * GET /tours/plan?date=2026-11-24&amp;maxVisits=5
 * </pre>
 */
@Path("/tours/plan")
@RolesAllowed(Roles.SALES_PROFESSIONAL)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Tourplanung")
@SecurityRequirement(name = TerraMaxApplication.BEARER_SCHEME)
public class TourPlanResource {

    static final int MIN_VISITS = 2;
    static final int MAX_VISITS = 10;
    static final int DEFAULT_VISITS = 5;

    @Inject
    TourPlanner planner;

    @Inject
    CurrentUser user;

    @Inject
    Clock clock;

    @GET
    @Operation(summary = "Besuchsplan vorschlagen", description = "Service Task \"Besuchsplan erzeugen\". Auswahl: "
            + "Kunden des eigenen Händlers mit Koordinaten, nie besuchte zuerst, dann am längsten nicht besuchte; "
            + "wer am Tourtag oder danach schon besucht wurde, fällt heraus. Reihenfolge: Luftlinie ab Händler, "
            + "immer der nächstgelegene offene Kunde. Wird nicht gespeichert; die Tour legt POST /tours an. "
            + "Format exakt wie TourPlan der process-engine (bisher tour-service).")
    @APIResponse(responseCode = "200", description = "Plan, kann weniger als zwei Besuche enthalten (Prozess endet dann mit \"Keine Tour möglich\")", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourPlanDto.class), examples = @ExampleObject(name = "Plan", value = """
            {"date": "2026-11-24",
             "visits": [
               {"position": 1, "customer": {"id": "3", "name": "Testbetrieb 03 (fiktiv)", "location": "Mostviertel",
                 "position": {"latitude": 48.31, "longitude": 15.62}, "lastVisit": null}, "distanceFromPreviousKm": 12.4},
               {"position": 2, "customer": {"id": "1", "name": "Testbetrieb 01 (fiktiv)", "location": "Marchfeld",
                 "position": {"latitude": 48.235, "longitude": 16.65}, "lastVisit": "2026-09-02"}, "distanceFromPreviousKm": 30.3}],
             "totalDistanceKm": 42.7}""")))
    @APIResponse(responseCode = "400", description = "Ungültiges Datum oder maxVisits außerhalb 2-10", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "maxVisits", value = "{\"error\": \"maxVisits muss zwischen 2 und 10 liegen\"}")))
    @APIResponse(responseCode = "401", description = "Kein oder ungültiger Token")
    @APIResponse(responseCode = "403", description = "Keine Rolle sales_professional oder kein dealer_id im Token", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Händler aus dem Token steht nicht in der Datenbank", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public TourPlanDto plan(
            @Parameter(description = "Tag der Tour (JJJJ-MM-TT), ohne Angabe heute (Europe/Vienna)", example = "2026-11-24") @QueryParam("date") String date,
            @Parameter(description = "Gewünschte Anzahl Besuche, 2 bis 10, ohne Angabe 5", example = "5") @QueryParam("maxVisits") Integer maxVisits) {
        String dealerId = user.requireDealerId();
        LocalDate tourDate;
        try {
            tourDate = date == null || date.isBlank() ? LocalDate.now(clock) : LocalDate.parse(date.strip());
        } catch (DateTimeParseException e) {
            throw ApiErrors.badRequest("date muss im Format JJJJ-MM-TT angegeben werden, z. B. 2026-11-10");
        }
        int visits = maxVisits == null ? DEFAULT_VISITS : maxVisits;
        if (visits < MIN_VISITS || visits > MAX_VISITS) {
            throw ApiErrors.badRequest("maxVisits muss zwischen " + MIN_VISITS + " und " + MAX_VISITS + " liegen");
        }

        TourPlanProposal proposal;
        try {
            proposal = planner.propose(dealerId, new PlanningConstraints(tourDate, visits));
        } catch (IllegalArgumentException e) {
            throw ApiErrors.notFound(e.getMessage());
        }
        Map<Long, Customer> customers = proposal.stops().stream().map(ProposedStop::customerId)
                .map(id -> Customer.<Customer> findById(id))
                .collect(Collectors.toMap(c -> c.id, Function.identity()));
        return TourPlanDto.from(proposal, customers);
    }
}
