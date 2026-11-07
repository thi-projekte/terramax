package de.thi.terramax;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/**
 * Platzhalter für Funktionen des Dealer Principals (z. B. Marktkarte, #17/#26). Zeigt die Gegenrichtung: ein Sales
 * Professional bekommt hier 403.
 */
@Path("/dealer")
@RolesAllowed(Roles.DEALER_PRINCIPAL)
public class DealerResource {

    @Inject
    SecurityIdentity identity;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> dealer() {
        return Map.of(
                "message", "Hello " + identity.getPrincipal().getName() + ", du bist als Dealer Principal angemeldet");
    }
}
