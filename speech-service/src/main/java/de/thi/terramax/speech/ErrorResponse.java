package de.thi.terramax.speech;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Fehlermeldung für die Oberfläche: {"message": "..."}.
 */
public record ErrorResponse(String message) {

    static Response of(Status status, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(new ErrorResponse(message)).build();
    }

    static WebApplicationException exception(Status status, String message) {
        return new WebApplicationException(of(status, message));
    }
}
