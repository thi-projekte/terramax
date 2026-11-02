package de.thi.terramax;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/**
 * Liefert dem Frontend, wer angemeldet ist und welche TerraMax-Rollen er hat.
 * Jeder angemeldete Benutzer darf das abfragen – auch ohne Fachrolle.
 */
@Path("/me")
@Authenticated
public class MeResource {

    @Inject
    SecurityIdentity identity;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> me() {
        List<String> roles = identity.getRoles().stream()
                .filter(Roles.ALL::contains)
                .sorted()
                .toList();
        return Map.of(
                "username", identity.getPrincipal().getName(),
                "roles", roles
        );
    }
}
