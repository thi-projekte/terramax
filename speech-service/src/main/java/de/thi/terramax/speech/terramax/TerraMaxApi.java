package de.thi.terramax.speech.terramax;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Ausschnitt der REST-API des terramax-service (terramax-service/README.md, "Endpunkte"). Jeder Aufruf trägt das
 * Keycloak-Token des Nutzers aus der SPA; der terramax-service prüft es selbst und ordnet das Protokoll diesem Nutzer
 * zu ({@code createdBy = sub}). Der speech-service braucht dafür kein eigenes Konto.
 */
@RegisterRestClient(configKey = "terramax")
@Produces(MediaType.APPLICATION_JSON)
public interface TerraMaxApi {

    @POST
    @Path("/tours/{tourId}/protocols")
    @Consumes(MediaType.APPLICATION_JSON)
    SavedProtocol createProtocol(@PathParam("tourId") long tourId, CreateProtocolRequest request,
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization);

    @POST
    @Path("/leads")
    @Consumes(MediaType.APPLICATION_JSON)
    JsonNode createLead(CreateLeadRequest request, @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization);

    /**
     * Request von {@code POST /leads} (CreateLeadRequest im terramax-service). Die Antwort (LeadDto) reicht der
     * speech-service unverändert an die SPA weiter, deshalb {@link JsonNode}.
     */
    record CreateLeadRequest(String source, String eventName, LeadContact contact, Double latitude, Double longitude,
            String comment, String transcript, Long existingCustomerId) {
    }

    /** Daten von der Visitenkarte (CreateLeadRequest.Contact im terramax-service). */
    record LeadContact(String name, String company, String role, String phone, String email, String street,
            String postalCode, String city) {
    }

    /** Request von {@code POST /tours/{tourId}/protocols} (CreateProtocolRequest im terramax-service). */
    record CreateProtocolRequest(Long customerId, String contactPerson, List<String> topics, String outcome,
            List<String> nextSteps, LocalDate followUpDate, String transcript) {
    }

    /** Antwort (ProtocolDto im terramax-service). Weitere Felder werden ignoriert. */
    record SavedProtocol(Long id, Long tourId, Long customerId, String contactPerson, List<String> topics,
            String outcome, List<String> nextSteps, LocalDate followUpDate, String transcript, String createdBy,
            OffsetDateTime createdAt) {
    }

    /** Fehlerformat des terramax-service. */
    record TerraMaxError(String error) {
    }
}
