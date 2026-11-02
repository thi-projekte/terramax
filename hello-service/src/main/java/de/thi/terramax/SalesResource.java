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
 * Platzhalter für Funktionen des Sales Professionals (z. B. Hinweise und Aufgaben, #24/#29).
 * Nur mit Rolle sales_professional erreichbar – sonst 403, ohne Token 401.
 */
@Path("/sales")
@RolesAllowed(Roles.SALES_PROFESSIONAL)
public class SalesResource {

    @Inject
    SecurityIdentity identity;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> sales() {
        return Map.of(
                "message", "Hello " + identity.getPrincipal().getName() + ", du bist als Sales Professional angemeldet"
        );
    }
}
