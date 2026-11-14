package de.thi.terramax;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Liefert dem Frontend, wer angemeldet ist, welche TerraMax-Rollen er hat und (falls vorhanden) seine Händlerzuordnung
 * (dealer_id-Claim aus dem Token). Jeder angemeldete Benutzer darf das abfragen – auch ohne Fachrolle.
 */
@Path("/me")
@Authenticated
public class MeResource {

    @Inject
    SecurityIdentity identity;

    @Inject
    JsonWebToken jwt;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> me() {
        List<String> roles = identity.getRoles().stream()
                .filter(Roles.ALL::contains)
                .sorted()
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("username", identity.getPrincipal().getName());
        result.put("roles", roles);

        // dealer_id ist optional: nicht jeder Nutzer ist einem Händler zugeordnet
        // (z. B. testuser hat keine Fachrolle und damit auch kein dealer_id-Attribut)
        String dealerId = jwt.getClaim("dealer_id");
        if (dealerId != null) {
            result.put("dealerId", dealerId);
        }

        return result;
    }
}
