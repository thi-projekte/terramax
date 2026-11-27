package de.thi.terramax.speech;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Set;

/**
 * Schnellerfassung (#134): liest die Daten einer fotografierten Visitenkarte und gibt einen Entwurf zurück. Der Entwurf
 * wird nicht gespeichert: Der Sales Professional prüft ihn in der Oberfläche und speichert ihn über
 * {@link LeadResource}. Foto und Kontaktdaten werden weder gespeichert noch geloggt; das Foto geht an OpenAI.
 */
@Path("/business-cards")
@RolesAllowed(Roles.SALES_PROFESSIONAL)
public class BusinessCardResource {

    private static final Logger LOG = Logger.getLogger(BusinessCardResource.class);
    /** Bildformate, die OpenAI annimmt und die Handykameras bzw. Browser liefern. */
    static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    @Inject
    BusinessCardReader reader;

    @Inject
    SpeechEvents events;

    public record BusinessCardResponse(String captureId, BusinessCard card) {
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public BusinessCardResponse read(@RestForm("image") FileUpload image, @RestForm("captureId") String captureId) {
        if (image == null) {
            throw ErrorResponse.exception(Status.BAD_REQUEST, "Es wurde kein Foto übertragen.");
        }
        if (captureId == null || captureId.isBlank()) {
            throw ErrorResponse.exception(Status.BAD_REQUEST, "Die captureId fehlt.");
        }
        String mediaType = Transcriber.mediaType(image.contentType()).toLowerCase();
        if (!IMAGE_TYPES.contains(mediaType)) {
            throw ErrorResponse.exception(Status.UNSUPPORTED_MEDIA_TYPE,
                    "Das Foto muss ein JPEG-, PNG- oder WebP-Bild sein.");
        }

        // Die temporäre Upload-Datei löscht Quarkus nach der Anfrage (delete-uploaded-files-on-end)
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(image.uploadedFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (bytes.length == 0) {
            throw ErrorResponse.exception(Status.BAD_REQUEST, "Das Foto ist leer.");
        }

        BusinessCard card;
        try {
            card = reader.read(bytes, mediaType);
        } catch (ProtocolGenerationException e) {
            events.businessCardFailed(captureId, e.reason().status());
            throw e;
        }
        events.businessCardRead(captureId);
        LOG.infof("Visitenkarte für Erfassung %s gelesen", captureId);
        return new BusinessCardResponse(captureId, card);
    }

    @ServerExceptionMapper
    public Response mapCardError(ProtocolGenerationException e) {
        LOG.warnf("Visitenkarte konnte nicht gelesen werden (%s): %s", e.reason(), e.getMessage());
        Status status = Status.fromStatusCode(e.reason().status());
        return switch (e.reason()) {
            case RATE_LIMITED ->
                    ErrorResponse.of(status, "Die AI ist gerade ausgelastet. Bitte gleich erneut versuchen.");
            case NOT_CONFIGURED, UNAVAILABLE -> ErrorResponse.of(status,
                    "Die AI ist gerade nicht erreichbar. Die Daten können auch von Hand eingetragen werden.");
            case INVALID_RESPONSE -> ErrorResponse.of(status,
                    "Die Visitenkarte konnte nicht gelesen werden. Bitte ein schärferes Foto versuchen.");
        };
    }
}
