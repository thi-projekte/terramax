package de.thi.terramax.speech;

import de.thi.terramax.speech.ProtocolGenerationException.Reason;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prüft den Protokoll-Endpunkt mit simulierter Anmeldung und simuliertem LLM.
 */
@QuarkusTest
class ProtocolResourceTest {

    @Inject
    FakeProtocolGenerator generator;

    @BeforeEach
    void resetFake() {
        generator.reset();
    }

    private static RequestSpecification request(String visitId, String transcript) {
        return given().contentType(ContentType.JSON).body(Map.of("visitId", visitId, "transcript", transcript));
    }

    // --- Erfolg ---

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void salesProfessionalGetsProtocol() {
        request("visit-1", "  Besuch beim Hof Maier.  ")
                .when().post("/protocols")
                .then().statusCode(200)
                .body("visitId", is("visit-1"))
                .body("protocol.contactPerson", is("Herr Maier (Betriebsleiter)"))
                .body("protocol.topics", contains("8R 410", "Finanzierung"))
                .body("protocol.outcome", is("Interesse an einer Vorführung."))
                .body("protocol.nextSteps", contains("Angebot schicken"))
                .body("protocol.followUpDate", is("2026-11-28"));

        assertEquals("Besuch beim Hof Maier.", generator.lastTranscript());
        assertEquals(LocalDate.now(ZoneId.of("Europe/Berlin")), generator.lastToday());
    }

    // --- Rollen ---

    @Test
    @TestSecurity(user = "testuser")
    void userWithoutRoleIsForbidden() {
        request("visit-1", "Text").when().post("/protocols").then().statusCode(403);
    }

    @Test
    void anonymousIsUnauthorized() {
        request("visit-1", "Text").when().post("/protocols").then().statusCode(401);
    }

    // --- Eingaben ---

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void emptyTranscriptIsRejected() {
        request("visit-1", "   ").when().post("/protocols")
                .then().statusCode(400).body("message", notNullValue());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void missingVisitIdIsRejected() {
        request("", "Text").when().post("/protocols")
                .then().statusCode(400).body("message", notNullValue());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void tooLongTranscriptIsRejected() {
        request("visit-1", "a".repeat(ProtocolResource.MAX_TRANSCRIPT_LENGTH + 1)).when().post("/protocols")
                .then().statusCode(400).body("message", notNullValue());
    }

    // --- Fehler des LLM ---

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void rateLimitReturns503() {
        generator.failWith(Reason.RATE_LIMITED);
        request("visit-1", "Text").when().post("/protocols")
                .then().statusCode(503).body("message", notNullValue());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void missingKeyReturns502() {
        generator.failWith(Reason.NOT_CONFIGURED);
        request("visit-1", "Text").when().post("/protocols")
                .then().statusCode(502).body("message", notNullValue());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void invalidResponseReturns502() {
        generator.failWith(Reason.INVALID_RESPONSE);
        request("visit-1", "Text").when().post("/protocols")
                .then().statusCode(502).body("message", notNullValue());
    }
}
