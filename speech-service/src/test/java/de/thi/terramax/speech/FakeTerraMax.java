package de.thi.terramax.speech;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spielt in den Tests den terramax-service ({@code POST /tours/{tourId}/protocols}). Läuft im selben Prozess unter
 * {@code /fake-terramax}, sodass der echte REST-Client samt Header-Weitergabe getestet wird. Merkt sich den letzten
 * Aufruf; Fehlerantworten lassen sich vorgeben. Zugriff nur über Methoden (Proxy der Bean).
 */
@Path("/fake-terramax/tours/{tourId}/protocols")
@ApplicationScoped
public class FakeTerraMax {

    static final long PROTOCOL_ID = 42;

    private int failStatus;
    private String failMessage;
    private Long lastTourId;
    private JsonNode lastBody;
    private String lastAuthorization;
    private int calls;

    void reset() {
        failStatus = 0;
        failMessage = null;
        lastTourId = null;
        lastBody = null;
        lastAuthorization = null;
        calls = 0;
    }

    /** Nächste Antworten mit diesem Status und {"error": message}, wie der terramax-service. */
    void failWith(int status, String message) {
        failStatus = status;
        failMessage = message;
    }

    Long lastTourId() {
        return lastTourId;
    }

    JsonNode lastBody() {
        return lastBody;
    }

    String lastAuthorization() {
        return lastAuthorization;
    }

    int calls() {
        return calls;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(@PathParam("tourId") long tourId, JsonNode body,
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        calls++;
        lastTourId = tourId;
        lastBody = body;
        lastAuthorization = authorization;
        if (failStatus != 0) {
            return Response.status(failStatus).type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", failMessage)).build();
        }
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("id", PROTOCOL_ID);
        saved.put("tourId", tourId);
        saved.put("customerId", body.get("customerId").asLong());
        saved.put("contactPerson", body.get("contactPerson").asText());
        saved.put("topics", body.get("topics"));
        saved.put("outcome", body.get("outcome").asText());
        saved.put("nextSteps", body.get("nextSteps"));
        saved.put("followUpDate", body.get("followUpDate").asText());
        saved.put("transcript", body.get("transcript").asText());
        saved.put("createdBy", "sub-of-sales");
        saved.put("createdAt", "2026-11-17T10:15:30+01:00");
        // Unbekannte Felder darf der speech-service ignorieren
        saved.put("somethingNew", true);
        return Response.status(Response.Status.CREATED).entity(saved).build();
    }
}
