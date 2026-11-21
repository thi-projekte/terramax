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

/**
 * Nimmt eine Sprachnotiz zu einem Kundenbesuch an und gibt das Transkript zurück. Nur für Sales Professionals – sonst
 * 403, ohne Token 401. Audio und Transkript werden nicht gespeichert und nicht geloggt.
 * <p>
 * Dasselbe als Stream: {@link TranscriptionSocket}.
 */
@Path("/transcriptions")
@RolesAllowed(Roles.SALES_PROFESSIONAL)
public class TranscriptionResource {

    private static final Logger LOG = Logger.getLogger(TranscriptionResource.class);

    @Inject
    Transcriber transcriber;

    public record TranscriptionResponse(String visitId, String transcript, String language, Double durationSeconds) {

        static TranscriptionResponse of(String visitId, Transcription transcription) {
            return new TranscriptionResponse(visitId, transcription.transcript(), transcription.language(),
                    transcription.durationSeconds());
        }
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public TranscriptionResponse transcribe(@RestForm("audio") FileUpload audio, @RestForm("visitId") String visitId) {
        if (audio == null) {
            throw ErrorResponse.exception(Status.BAD_REQUEST, "Es wurde keine Audiodatei übertragen.");
        }
        if (visitId == null || visitId.isBlank()) {
            throw ErrorResponse.exception(Status.BAD_REQUEST, "Die visitId fehlt.");
        }
        String mediaType = Transcriber.mediaType(audio.contentType());
        if (!mediaType.startsWith("audio/")) {
            throw ErrorResponse.exception(Status.UNSUPPORTED_MEDIA_TYPE, "Die Datei ist keine Audiodatei.");
        }

        // Die temporäre Upload-Datei löscht Quarkus nach der Anfrage (delete-uploaded-files-on-end)
        Transcription transcription = transcriber.transcribe(visitId, audio.uploadedFile(), audio.fileName(),
                mediaType);
        return TranscriptionResponse.of(visitId, transcription);
    }

    @ServerExceptionMapper
    public Response mapSpeechToTextError(SpeechToTextException e) {
        LOG.warnf("Transkription fehlgeschlagen: %s", e.getMessage());
        return ErrorResponse.of(Status.fromStatusCode(e.reason().status()), e.reason().userMessage());
    }
}
