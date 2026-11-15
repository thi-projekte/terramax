package de.thi.terramax.common;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Fehler mit Antwort im Format {@link ErrorResponse}.
 */
public final class ApiErrors {

    private ApiErrors() {
    }

    public static BadRequestException badRequest(String message) {
        return new BadRequestException(response(Status.BAD_REQUEST, message));
    }

    public static ForbiddenException forbidden(String message) {
        return new ForbiddenException(response(Status.FORBIDDEN, message));
    }

    public static NotFoundException notFound(String message) {
        return new NotFoundException(response(Status.NOT_FOUND, message));
    }

    public static ClientErrorException conflict(String message) {
        return new ClientErrorException(response(Status.CONFLICT, message));
    }

    public static Response response(Status status, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(new ErrorResponse(message)).build();
    }
}
