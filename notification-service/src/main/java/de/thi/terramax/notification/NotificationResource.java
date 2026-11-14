package de.thi.terramax.notification;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Liefert die Benachrichtigungen des angemeldeten Benutzers. Nur mit gültigem Bearer-Token erreichbar, sonst 401.
 */
@Path("/notifications")
@Authenticated
public class NotificationResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> list() {
        return List.of();
    }
}
