package de.thi.terramax.notification;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

/**
 * Liefert die Benachrichtigungen des angemeldeten Benutzers. Nur mit gültigem Bearer-Token erreichbar, sonst 401.
 */
@Path("/notifications")
@Authenticated
public class NotificationResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    NotificationStore store;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Notification> list() {
        return store.forUser(jwt.getSubject());
    }
}
