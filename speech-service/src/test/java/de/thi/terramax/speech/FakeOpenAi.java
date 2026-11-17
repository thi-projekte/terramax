package de.thi.terramax.speech;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;
import java.util.Map;

/**
 * Spielt in den Tests die Transkriptions-API von OpenAI ({@code POST /v1/audio/transcriptions}) unter
 * {@code /fake-openai}. Merkt sich die letzte Anfrage; Antwort und Fehler lassen sich vorgeben. Zugriff nur über
 * Methoden (Proxy der Bean).
 */
@Path("/fake-openai/audio/transcriptions")
@ApplicationScoped
public class FakeOpenAi {

    record Request(String authorization, String model, String responseFormat, List<String> languages,
            List<String> keywords, String prompt, String fileName, String fileType, long fileSize) {
    }

    private int failStatus;
    private Map<String, Object> answer;
    private Request lastRequest;

    void reset() {
        failStatus = 0;
        answer = Map.of("text", " Besuch beim Hof Maier. ", "languages", List.of(Map.of("code", "de")),
                "usage", Map.of("type", "tokens", "input_tokens", 120, "output_tokens", 10, "total_tokens", 130));
        lastRequest = null;
    }

    void failWith(int status) {
        failStatus = status;
    }

    void answerWith(Map<String, Object> answer) {
        this.answer = answer;
    }

    Request lastRequest() {
        return lastRequest;
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response transcribe(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @RestForm("model") String model, @RestForm("response_format") String responseFormat,
            @RestForm("languages[]") List<String> languages, @RestForm("keywords[]") List<String> keywords,
            @RestForm("prompt") String prompt, @RestForm("file") FileUpload file) {
        lastRequest = new Request(authorization, model, responseFormat, languages, keywords, prompt,
                file == null ? null : file.fileName(), file == null ? null : file.contentType(),
                file == null ? -1 : file.size());
        if (failStatus != 0) {
            return Response.status(failStatus).type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", Map.of("message", "simuliert"))).build();
        }
        return Response.ok(answer).build();
    }
}
