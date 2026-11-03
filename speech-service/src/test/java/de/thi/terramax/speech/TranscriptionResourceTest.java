package de.thi.terramax.speech;

import de.thi.terramax.speech.SpeechToTextException.Reason;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prüft den Endpunkt mit simulierter Anmeldung (@TestSecurity) und simuliertem Whisper.
 * 200 = Sales Professional, 403 = angemeldet ohne Rolle, 401 = nicht angemeldet.
 */
@QuarkusTest
class TranscriptionResourceTest {

    private static final byte[] AUDIO = {1, 2, 3, 4};

    @Inject
    FakeSpeechToTextClient speechToText;

    @BeforeEach
    void resetFake() {
        speechToText.reset();
    }

    private static RequestSpecification upload(String mediaType) {
        return given()
                .multiPart("audio", "voice-note.webm", AUDIO, mediaType)
                .multiPart("visitId", "visit-1");
    }

    // --- Erfolg ---

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void salesProfessionalGetsTranscript() {
        upload("audio/webm;codecs=opus")
                .when().post("/transcriptions")
                .then().statusCode(200)
                .body("visitId", is("visit-1"))
                .body("transcript", is("Besuch beim Hof Maier."))
                .body("language", is("de"))
                .body("durationSeconds", is(12.5f));

        assertEquals("voice-note.webm", speechToText.lastFileName());
        assertEquals("audio/webm", speechToText.lastMediaType());
    }

    // --- Rollen ---

    @Test
    @TestSecurity(user = "testuser")
    void userWithoutRoleIsForbidden() {
        upload("audio/webm").when().post("/transcriptions").then().statusCode(403);
    }

    @Test
    void anonymousIsUnauthorized() {
        upload("audio/webm").when().post("/transcriptions").then().statusCode(401);
    }

    // --- Ungültige Anfragen ---

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void missingAudioIsBadRequest() {
        given().multiPart("visitId", "visit-1")
                .when().post("/transcriptions")
                .then().statusCode(400)
                .body("message", notNullValue());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void missingVisitIdIsBadRequest() {
        given().multiPart("audio", "voice-note.webm", AUDIO, "audio/webm")
                .when().post("/transcriptions")
                .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void nonAudioFileIsRejected() {
        upload("application/pdf").when().post("/transcriptions").then().statusCode(415);
    }

    // --- Fehler der Spracherkennung ---

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void unreadableAudioIsUnsupportedMediaType() {
        speechToText.failWith(Reason.UNSUPPORTED_AUDIO);
        upload("audio/webm").when().post("/transcriptions").then().statusCode(415);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void modelNotReadyIsServiceUnavailable() {
        speechToText.failWith(Reason.MODEL_NOT_READY);
        upload("audio/webm").when().post("/transcriptions")
                .then().statusCode(503)
                .body("message", notNullValue());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void unavailableWhisperIsBadGateway() {
        speechToText.failWith(Reason.UNAVAILABLE);
        upload("audio/webm").when().post("/transcriptions").then().statusCode(502);
    }
}
