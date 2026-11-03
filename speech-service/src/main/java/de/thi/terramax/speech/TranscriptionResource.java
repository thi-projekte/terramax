package de.thi.terramax.speech;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Nimmt eine Sprachnotiz zu einem Kundenbesuch an und gibt das Transkript zurück.
 * Nur für Sales Professionals – sonst 403, ohne Token 401.
 * Audio und Transkript werden nicht gespeichert und nicht geloggt.
 */
@Path("/transcriptions")
@RolesAllowed(Roles.SALES_PROFESSIONAL)
public class TranscriptionResource {

    private static final Logger LOG = Logger.getLogger(TranscriptionResource.class);

    @Inject
    SpeechToTextClient speechToText;

    public record TranscriptionResponse(String visitId, String transcript, String language, double durationSeconds) {
    }

    public record ErrorResponse(String message) {
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public TranscriptionResponse transcribe(@RestForm("audio") FileUpload audio, @RestForm("visitId") String visitId) {
        if (audio == null) {
            throw error(Status.BAD_REQUEST, "Es wurde keine Audiodatei übertragen.");
        }
        if (visitId == null || visitId.isBlank()) {
            throw error(Status.BAD_REQUEST, "Die visitId fehlt.");
        }
        // Browser senden z. B. "audio/webm;codecs=opus"
        String mediaType = audio.contentType() == null ? "" : audio.contentType().split(";")[0].strip();
        if (!mediaType.startsWith("audio/")) {
            throw error(Status.UNSUPPORTED_MEDIA_TYPE, "Die Datei ist keine Audiodatei.");
        }

        // Die temporäre Upload-Datei löscht Quarkus nach der Anfrage (delete-uploaded-files-on-end)
        Transcription transcription = speechToText.transcribe(audio.uploadedFile(), audio.fileName(), mediaType);
        LOG.infof("Sprachnotiz für Besuch %s transkribiert (%.0f s Audio)", visitId, transcription.durationSeconds());

        return new TranscriptionResponse(visitId, transcription.transcript(), transcription.language(),
                transcription.durationSeconds());
    }

    @ServerExceptionMapper
    public Response mapSpeechToTextError(SpeechToTextException e) {
        LOG.warnf("Transkription fehlgeschlagen: %s", e.getMessage());
        return switch (e.reason()) {
            case UNSUPPORTED_AUDIO -> errorResponse(Status.UNSUPPORTED_MEDIA_TYPE,
                    "Die Audiodatei konnte nicht gelesen werden.");
            case MODEL_NOT_READY -> errorResponse(Status.SERVICE_UNAVAILABLE,
                    "Die Spracherkennung startet noch. Bitte in ein paar Minuten erneut versuchen.");
            case UNAVAILABLE -> errorResponse(Status.BAD_GATEWAY,
                    "Die Spracherkennung ist gerade nicht erreichbar.");
        };
    }

    private static WebApplicationException error(Status status, String message) {
        return new WebApplicationException(errorResponse(status, message));
    }

    private static Response errorResponse(Status status, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(new ErrorResponse(message)).build();
    }
}
