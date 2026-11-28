package de.thi.terramax.speech;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spielt in den Tests den terramax-service ({@code POST /leads}), wie {@link FakeTerraMax} für Protokolle. Merkt sich
 * den letzten Aufruf; Fehlerantworten lassen sich vorgeben. Zugriff nur über Methoden (Proxy der Bean).
 */
@Path("/fake-terramax/leads")
@ApplicationScoped
public class FakeTerraMaxLeads {

    static final long CUSTOMER_ID = 77;

    private int failStatus;
    private String failMessage;
    private JsonNode lastBody;
    private String lastAuthorization;
    private int calls;

    void reset() {
        failStatus = 0;
        failMessage = null;
        lastBody = null;
        lastAuthorization = null;
        calls = 0;
    }

    /** Nächste Antworten mit diesem Status und {"error": message}, wie der terramax-service. */
    void failWith(int status, String message) {
        failStatus = status;
        failMessage = message;
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
    public Response create(JsonNode body, @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        calls++;
        lastBody = body;
        lastAuthorization = authorization;
        if (failStatus != 0) {
            return Response.status(failStatus).type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", failMessage)).build();
        }
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("customerId", CUSTOMER_ID);
        saved.put("newCustomer", true);
        saved.put("status", "LEAD");
        saved.put("name", body.path("contact").path("company").asText());
        saved.put("contact", Map.of("id", 3, "name", body.path("contact").path("name").asText()));
        saved.put("note", Map.of("id", 9, "comment", body.path("comment").asText(), "createdBy", "sub-of-sales"));
        return Response.status(Response.Status.CREATED).entity(saved).build();
    }
}
