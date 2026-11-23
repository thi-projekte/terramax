package de.thi.terramax.tour;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import de.thi.terramax.TestData;
import de.thi.terramax.security.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * POST /tours/{id}/abort: Status aborted, optionaler Grund, idempotent, 409 bei abgeschlossener oder verworfener Tour.
 * Als Sales Professional auf seiner Tour own (huber, maier).
 */
@QuarkusTest
@TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
@OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
        @Claim(key = "dealer_id", value = TestData.DEALER) })
class TourAbortTest {

    @Inject
    TestData testData;

    private TestData.Fixture f;

    @BeforeEach
    void setUp() {
        f = testData.reset();
    }

    @Test
    void abortKeepsOpenStopsAndShowsInStatusAndOverview() {
        given().contentType("application/json").when()
                .post("/tours/{id}/stops/{c}/complete", f.ownTour(), f.huber()).then().statusCode(200);

        given().contentType("application/json").body(Map.of("reason", " Unwetter ")).when()
                .post("/tours/{id}/abort", f.ownTour()).then().statusCode(200).body("status", is("aborted"))
                .body("abortReason", is("Unwetter")).body("abortedAt", notNullValue())
                .body("customers.status", contains("DOKUMENTIERT", "OFFEN"));

        given().when().get("/tours/{id}/status", f.ownTour()).then().body("tourStatus", is("aborted"))
                .body("beendet", is(true)).body("abgebrochen", is(true)).body("abbruchGrund", is("Unwetter"))
                .body("offeneHalte", is(1)).body("alleErledigt", is(false)).body("naechsterHalt", nullValue());

        given().when().get("/tours/{id}/overview", f.ownTour()).then().body("tourStatus", is("aborted"))
                .body("abbruchGrund", is("Unwetter")).body("abgebrochenAm", notNullValue())
                .body("besuche.status", contains("DOKUMENTIERT", "OFFEN")).body("anzahlOffen", is(1));
    }

    @Test
    void abortWithoutBodyAndWithoutContentType() {
        given().when().post("/tours/{id}/abort", f.ownTour()).then().statusCode(200).body("status", is("aborted"))
                .body("abortReason", nullValue());
    }

    @Test
    void abortIsIdempotent() {
        given().contentType("application/json").body(Map.of("reason", "Unwetter")).when()
                .post("/tours/{id}/abort", f.ownTour()).then().statusCode(200);
        String first = given().when().get("/tours/{id}", f.ownTour()).then().extract().path("abortedAt");

        given().contentType("application/json").body(Map.of("reason", "Unwetter")).when()
                .post("/tours/{id}/abort", f.ownTour()).then().statusCode(200);
        given().when().post("/tours/{id}/abort", f.ownTour()).then().statusCode(200)
                .body("abortReason", is("Unwetter")).body("abortedAt", is(first));

        given().contentType("application/json").body(Map.of("reason", "Anderer Grund")).when()
                .post("/tours/{id}/abort", f.ownTour()).then().statusCode(409);
    }

    @Test
    void completedOrDiscardedTourCannotBeAborted() {
        given().contentType("application/json").body(Map.of("status", "completed")).when()
                .patch("/tours/{id}", f.ownTour()).then().statusCode(200);
        given().when().post("/tours/{id}/abort", f.ownTour()).then().statusCode(409).body("error", notNullValue());

        testData.setTourStatus(f.ownTour(), TourStatus.DISCARDED);
        given().when().post("/tours/{id}/abort", f.ownTour()).then().statusCode(409);
    }

    @Test
    void abortedTourCannotBeCompletedOrReopened() {
        given().when().post("/tours/{id}/abort", f.ownTour()).then().statusCode(200);

        given().contentType("application/json").body(Map.of("status", "completed")).when()
                .patch("/tours/{id}", f.ownTour()).then().statusCode(409);
        given().contentType("application/json").body(Map.of("status", "in_progress")).when()
                .patch("/tours/{id}", f.ownTour()).then().statusCode(409);
        given().contentType("application/json").when()
                .post("/tours/{id}/stops/{c}/complete", f.ownTour(), f.maier()).then().statusCode(409);
        given().contentType("application/json").body(Map.of("reason", "x")).when()
                .post("/tours/{id}/stops/{c}/skip", f.ownTour(), f.maier()).then().statusCode(409);
    }

    @Test
    void patchToAbortedIsBadRequest() {
        given().contentType("application/json").body(Map.of("status", "aborted")).when()
                .patch("/tours/{id}", f.ownTour()).then().statusCode(400);
    }

    @Test
    void patchSameFinalStatusIsIdempotent() {
        given().contentType("application/json").body(Map.of("status", "completed")).when()
                .patch("/tours/{id}", f.ownTour()).then().statusCode(200);
        given().contentType("application/json").body(Map.of("status", "completed")).when()
                .patch("/tours/{id}", f.ownTour()).then().statusCode(200);
        given().contentType("application/json").body(Map.of("status", "discarded")).when()
                .patch("/tours/{id}", f.ownTour()).then().statusCode(409);
    }

    @Test
    void invalidOrTooLongBodyIsBadRequest() {
        given().contentType("application/json").body("kein json").when().post("/tours/{id}/abort", f.ownTour())
                .then().statusCode(400);
        given().contentType("application/json").body(Map.of("reason", "x".repeat(1001))).when()
                .post("/tours/{id}/abort", f.ownTour()).then().statusCode(400);
    }

    @Test
    void tourOfColleagueIsNotFound() {
        given().when().post("/tours/{id}/abort", f.colleagueTour()).then().statusCode(404);
    }
}
