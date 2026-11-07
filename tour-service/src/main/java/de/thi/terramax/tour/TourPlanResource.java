package de.thi.terramax.tour;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Besuchsplan für einen Tag (#29, Teil 2). Nur mit Rolle sales_professional erreichbar – sonst 403, ohne Token 401.
 *
 * <pre>
 * GET /tours/plan?date=2026-11-10&amp;maxVisits=5
 * </pre>
 */
@Path("/tours/plan")
@RolesAllowed(Roles.SALES_PROFESSIONAL)
public class TourPlanResource {

    static final int MIN_VISITS = 2;
    static final int MAX_VISITS = 10;
    static final int DEFAULT_VISITS = 5;
    private static final ZoneId VIENNA = ZoneId.of("Europe/Vienna");

    @Inject
    VisitPlanner planner;

    /**
     * @param date
     *            Tag der Tour im Format JJJJ-MM-TT; ohne Angabe heute
     * @param maxVisits
     *            gewünschte Anzahl Besuche (2–10); ohne Angabe 5
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response plan(@QueryParam("date") String date, @QueryParam("maxVisits") Integer maxVisits) {
        LocalDate tourDate;
        try {
            tourDate = date == null || date.isBlank() ? LocalDate.now(VIENNA) : LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return badRequest("date muss im Format JJJJ-MM-TT angegeben werden, z. B. 2026-11-10");
        }

        int visits = maxVisits == null ? DEFAULT_VISITS : maxVisits;
        if (visits < MIN_VISITS || visits > MAX_VISITS) {
            return badRequest("maxVisits muss zwischen " + MIN_VISITS + " und " + MAX_VISITS + " liegen");
        }

        return Response.ok(planner.plan(tourDate, visits)).build();
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorResponse(message)).build();
    }
}
