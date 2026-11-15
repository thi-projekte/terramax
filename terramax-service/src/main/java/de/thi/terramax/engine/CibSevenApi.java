package de.thi.terramax.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.oidc.token.propagation.common.AccessToken;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Ausschnitt der REST-API von CIB Seven ({@code /engine-rest}). Basis-URL: {@code TERRAMAX_ENGINE_URL}.
 * <p>
 * {@link AccessToken} reicht das Keycloak-Token des Aufrufers weiter; die Engine prüft die Rollen selbst (siehe
 * process-engine/README.md).
 */
@RegisterRestClient(configKey = "process-engine")
@AccessToken
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface CibSevenApi {

    @POST
    @Path("/process-definition/key/{key}/start")
    StartedInstance start(@PathParam("key") String processDefinitionKey, StartRequest request);

    @GET
    @Path("/process-instance/{id}")
    InstanceState processInstance(@PathParam("id") String processInstanceId);

    record StartRequest(String businessKey, Map<String, Variable> variables) {
    }

    record Variable(Object value, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StartedInstance(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record InstanceState(String id, boolean ended, boolean suspended) {
    }
}
