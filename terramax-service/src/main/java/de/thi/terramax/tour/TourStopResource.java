package de.thi.terramax.tour;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.thi.terramax.common.ApiErrors;
import de.thi.terramax.common.ErrorResponse;
import de.thi.terramax.common.TerraMaxApplication;
import de.thi.terramax.security.Roles;
import de.thi.terramax.tour.StopTransitions.InvalidTransitionException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.OffsetDateTime;
import java.util.function.Supplier;
import org.eclipse.microprofile.openapi.annotations.Operation;
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
 * Stand eines Halts ändern: begonnen, übersprungen, dokumentiert. Alle Aufrufe sind idempotent (wiederholter Aufruf:
 * 200 ohne Änderung), ungültige Übergänge geben 409; Regeln in {@link StopTransitions}. Antwort ist immer der Halt nach
 * dem Aufruf.
 */
@Path("/tours/{tourId}/stops/{customerId}")
@RolesAllowed({ Roles.SALES_PROFESSIONAL, Roles.DEALER_PRINCIPAL, Roles.PROCESS_ENGINE })
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Touren: Halte")
@SecurityRequirement(name = TerraMaxApplication.BEARER_SCHEME)
@APIResponse(responseCode = "401", description = "Kein oder ungültiger Token")
@APIResponse(responseCode = "403", description = "Keine passende Rolle oder (für Nutzer) kein dealer_id im Token", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
@APIResponse(responseCode = "404", description = "Unbekannte oder nicht sichtbare Tour, oder Kunde nicht auf der Tour", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
@APIResponse(responseCode = "409", description = "Ungültiger Statusübergang, z. B. Überspringen eines dokumentierten Halts oder Tour bereits abgeschlossen", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(name = "Konflikt", value = "{\"error\": \"Halt 3 (DOKUMENTIERT) ist bereits dokumentiert und kann nicht übersprungen werden.\"}")))
public class TourStopResource {

    private static final Logger LOG = Logger.getLogger(TourStopResource.class);

    private static final String STOP_OPEN_STARTED = """
            {"customerId": 3, "externalId": "MOCK-003", "name": "Testbetrieb 03 (fiktiv)", "position": 1,
             "status": "OFFEN", "skipReason": null, "startedAt": "2026-11-24T09:15:00+01:00", "finishedAt": null,
             "latitude": 48.31, "longitude": 15.62}""";

    @Inject
    TourAccess access;

    @Inject
    ObjectMapper objectMapper;

    @POST
    @Path("/start")
    @Transactional
    @Operation(summary = "Besuch beginnen", description = "Setzt startedAt, der Status bleibt OFFEN. Wiederholt: "
            + "unverändert, der erste Zeitpunkt bleibt. 409, wenn der Halt schon erledigt ist. Setzt eine Tour von "
            + "planned auf in_progress.")
    @RequestBody(required = false, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourRequests.StopTime.class), examples = {
            @ExampleObject(name = "Jetzt", value = "{}"),
            @ExampleObject(name = "Mit Zeitpunkt", value = "{\"at\": \"2026-11-24T09:15:00+01:00\"}") }))
    @APIResponse(responseCode = "200", description = "Halt nach dem Aufruf", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourDto.Stop.class), examples = @ExampleObject(name = "Begonnen", value = STOP_OPEN_STARTED)))
    public TourDto.Stop start(@Parameter(description = "ID der Tour", example = "1") @PathParam("tourId") long tourId,
            @Parameter(description = "ID des Kunden", example = "3") @PathParam("customerId") long customerId,
            String body) {
        TourCustomer stop = stop(tourId, customerId);
        apply("start", stop, () -> StopTransitions.start(stop, at(stopTime(body))));
        return TourDto.Stop.from(stop);
    }

    @POST
    @Path("/skip")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(summary = "Besuch überspringen", description = "Status UEBERSPRUNGEN mit Grund, setzt finishedAt. "
            + "Wiederholt mit gleichem Grund: unverändert. 409 bei dokumentiertem Halt oder anderem Grund.")
    @RequestBody(required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourRequests.Skip.class), examples = @ExampleObject(name = "Überspringen", value = "{\"reason\": \"Betrieb nicht erreichbar\"}")))
    @APIResponse(responseCode = "200", description = "Halt nach dem Aufruf", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourDto.Stop.class), examples = @ExampleObject(name = "Übersprungen", value = """
            {"customerId": 1, "externalId": "MOCK-001", "name": "Testbetrieb 01 (fiktiv)", "position": 2,
             "status": "UEBERSPRUNGEN", "skipReason": "Betrieb nicht erreichbar", "startedAt": null,
             "finishedAt": "2026-11-24T10:30:00+01:00", "latitude": 48.235, "longitude": 16.65}""")))
    @APIResponse(responseCode = "400", description = "Grund fehlt oder ist zu lang", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public TourDto.Stop skip(@Parameter(description = "ID der Tour", example = "1") @PathParam("tourId") long tourId,
            @Parameter(description = "ID des Kunden", example = "1") @PathParam("customerId") long customerId,
            TourRequests.Skip request) {
        access.requireCaller();
        String reason = request == null || request.reason() == null ? null : request.reason().strip();
        if (reason == null || reason.isEmpty()) {
            throw ApiErrors.badRequest("Zum Überspringen ist ein Grund nötig (reason).");
        }
        if (reason.length() > TourRequests.MAX_SKIP_REASON_LENGTH) {
            throw ApiErrors.badRequest("Der Grund ist zu lang.");
        }
        TourCustomer stop = stop(tourId, customerId);
        apply("skip", stop, () -> StopTransitions.skip(stop, reason, at(request.at())));
        return TourDto.Stop.from(stop);
    }

    @POST
    @Path("/complete")
    @Transactional
    @Operation(summary = "Besuch abschließen (dokumentiert)", description = "Status DOKUMENTIERT, setzt finishedAt. "
            + "Geht auch ohne vorheriges start. Wiederholt: unverändert. 409 bei übersprungenem Halt. Das Protokoll "
            + "selbst speichert der speech-service vorher über POST /tours/{id}/protocols.")
    @RequestBody(required = false, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourRequests.StopTime.class), examples = {
            @ExampleObject(name = "Jetzt", value = "{}"),
            @ExampleObject(name = "Mit Zeitpunkt", value = "{\"at\": \"2026-11-24T09:55:00+01:00\"}") }))
    @APIResponse(responseCode = "200", description = "Halt nach dem Aufruf", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TourDto.Stop.class), examples = @ExampleObject(name = "Dokumentiert", value = """
            {"customerId": 3, "externalId": "MOCK-003", "name": "Testbetrieb 03 (fiktiv)", "position": 1,
             "status": "DOKUMENTIERT", "skipReason": null, "startedAt": "2026-11-24T09:15:00+01:00",
             "finishedAt": "2026-11-24T09:55:00+01:00", "latitude": 48.31, "longitude": 15.62}""")))
    @APIResponse(responseCode = "400", description = "Abschluss liegt vor dem Beginn", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorResponse.class)))
    public TourDto.Stop complete(
            @Parameter(description = "ID der Tour", example = "1") @PathParam("tourId") long tourId,
            @Parameter(description = "ID des Kunden", example = "3") @PathParam("customerId") long customerId,
            String body) {
        TourCustomer stop = stop(tourId, customerId);
        apply("complete", stop, () -> StopTransitions.complete(stop, at(stopTime(body))));
        return TourDto.Stop.from(stop);
    }

    @DELETE
    @Transactional
    @Operation(summary = "Halt aus dem Plan entfernen", description = "Für \"Besuchsplan prüfen\": entfernt einen "
            + "Kunden aus einer Tour im Status planned und nummeriert die übrigen Halte neu. Nur für Halte, die OFFEN "
            + "und nicht begonnen sind. Idempotent: Ist der Kunde nicht (mehr) auf der Tour, ebenfalls 204.")
    @APIResponse(responseCode = "204", description = "Entfernt oder war nicht auf der Tour")
    public void remove(@Parameter(description = "ID der Tour", example = "1") @PathParam("tourId") long tourId,
            @Parameter(description = "ID des Kunden", example = "2") @PathParam("customerId") long customerId) {
        Tour tour = access.visibleTour(tourId);
        TourCustomer stop = tour.stop(customerId).orElse(null);
        if (stop == null) {
            return;
        }
        if (tour.status != TourStatus.PLANNED) {
            throw ApiErrors.conflict("Halte lassen sich nur aus einer geplanten Tour entfernen (Tour ist "
                    + tour.status.getDbValue() + ").");
        }
        if (stop.status != StopStatus.OFFEN || stop.startedAt != null) {
            throw ApiErrors.conflict("Halt " + customerId + " ist schon begonnen oder erledigt.");
        }
        tour.stops.remove(stop);
        int position = 1;
        for (TourCustomer s : tour.stops) {
            s.position = position++;
        }
        LOG.infof("Halt %d aus Tour %d entfernt", customerId, tour.id);
    }

    private TourCustomer stop(long tourId, long customerId) {
        Tour tour = access.visibleTour(tourId);
        return tour.stop(customerId)
                .orElseThrow(() -> ApiErrors.notFound("Kunde " + customerId + " ist nicht auf Tour " + tourId));
    }

    private static void apply(String action, TourCustomer stop, Supplier<Boolean> transition) {
        boolean changed;
        try {
            changed = transition.get();
        } catch (InvalidTransitionException e) {
            throw ApiErrors.conflict(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw ApiErrors.badRequest(e.getMessage());
        }
        if (changed) {
            LOG.infof("Tour %d, Halt %d: %s -> %s", stop.tour.id, stop.customer.id, action, stop.status);
        }
    }

    private static OffsetDateTime at(OffsetDateTime requested) {
        return requested == null ? OffsetDateTime.now() : requested;
    }

    /**
     * Der Body von start und complete ist optional. Er wird als Text gelesen und selbst geparst, damit die Engine auch
     * ohne Body und mit beliebigem (oder ohne) Content-Type aufrufen kann; sonst antwortet der JSON-Reader mit 415.
     */
    private OffsetDateTime stopTime(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            TourRequests.StopTime request = objectMapper.readValue(body, TourRequests.StopTime.class);
            return request == null ? null : request.at();
        } catch (JsonProcessingException e) {
            throw ApiErrors.badRequest("Der Body muss leer sein oder {\"at\": \"<ISO-8601-Zeitpunkt>\"} lauten.");
        }
    }
}
