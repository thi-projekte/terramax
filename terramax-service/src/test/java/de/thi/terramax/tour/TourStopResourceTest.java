package de.thi.terramax.tour;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.thi.terramax.TestData;
import de.thi.terramax.security.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Statusübergänge der Halte über die API: idempotent bei Wiederholung, 409 bei ungültigem Übergang. Alle Tests als
 * Sales Professional {@value TestData#SALES_SUB} auf seiner Tour own (huber, maier).
 */
@QuarkusTest
@TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
@OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
        @Claim(key = "dealer_id", value = TestData.DEALER) })
class TourStopResourceTest {

    @Inject
    TestData testData;

    private TestData.Fixture f;

    @BeforeEach
    void setUp() {
        f = testData.reset();
    }

    private ValidatableResponse post(String action, long customerId, Object body) {
        var request = given().contentType("application/json");
        if (body != null) {
            request.body(body);
        }
        return request.when().post("/tours/{id}/stops/{c}/" + action, f.ownTour(), customerId).then();
    }

    @Test
    void startIsIdempotentAndKeepsFirstTime() {
        post("start", f.huber(), Map.of("at", "2026-11-20T09:00:00+01:00")).statusCode(200)
                .body("status", is("OFFEN")).body("startedAt", notNullValue()).body("finishedAt", nullValue());
        String first = given().when().get("/tours/{id}", f.ownTour()).then().extract().path("customers[0].startedAt");

        post("start", f.huber(), Map.of("at", "2026-11-20T11:00:00+01:00")).statusCode(200);

        given().when().get("/tours/{id}", f.ownTour()).then().body("status", is("in_progress"))
                .body("customers[0].startedAt", is(first));
    }

    @Test
    void startWithoutBodyUsesServerTime() {
        given().when().post("/tours/{id}/stops/{c}/start", f.ownTour(), f.huber()).then().statusCode(200)
                .body("startedAt", notNullValue());
    }

    @Test
    void invalidTimeBodyIsBadRequest() {
        post("start", f.huber(), Map.of("at", "gestern")).statusCode(400);
    }

    @Test
    void completeIsIdempotent() {
        post("complete", f.huber(), null).statusCode(200).body("status", is("DOKUMENTIERT"))
                .body("finishedAt", notNullValue());
        String first = given().when().get("/tours/{id}", f.ownTour()).then().extract()
                .path("customers[0].finishedAt");

        post("complete", f.huber(), null).statusCode(200).body("status", is("DOKUMENTIERT"));

        given().when().get("/tours/{id}", f.ownTour()).then().body("customers[0].finishedAt", is(first));
    }

    @Test
    void completeAfterStart() {
        post("start", f.huber(), Map.of("at", "2026-11-20T09:00:00+01:00")).statusCode(200);
        post("complete", f.huber(), Map.of("at", "2026-11-20T09:45:00+01:00")).statusCode(200)
                .body("status", is("DOKUMENTIERT")).body("startedAt", notNullValue());
    }

    @Test
    void completeBeforeStartTimeIsBadRequest() {
        post("start", f.huber(), Map.of("at", "2026-11-20T09:00:00+01:00")).statusCode(200);
        post("complete", f.huber(), Map.of("at", "2026-11-20T08:00:00+01:00")).statusCode(400);
    }

    @Test
    void documentedStopCannotBeSkippedOrStarted() {
        post("complete", f.huber(), null).statusCode(200);

        post("skip", f.huber(), Map.of("reason", "doch nicht")).statusCode(409).body("error", notNullValue());
        post("start", f.huber(), null).statusCode(409);
    }

    @Test
    void skipNeedsReason() {
        post("skip", f.maier(), Map.of()).statusCode(400).body("error",
                is("Zum Überspringen ist ein Grund nötig (reason)."));
        post("skip", f.maier(), Map.of("reason", "   ")).statusCode(400);
        post("skip", f.maier(), Map.of("reason", "x".repeat(1001))).statusCode(400);
    }

    @Test
    void skipIsIdempotentWithSameReason() {
        post("skip", f.maier(), Map.of("reason", " Betrieb nicht erreichbar ")).statusCode(200)
                .body("status", is("UEBERSPRUNGEN")).body("skipReason", is("Betrieb nicht erreichbar"))
                .body("finishedAt", notNullValue());

        post("skip", f.maier(), Map.of("reason", "Betrieb nicht erreichbar")).statusCode(200);
        post("skip", f.maier(), Map.of("reason", "Anderer Grund")).statusCode(409);
    }

    @Test
    void skippedStopCannotBeCompletedOrStarted() {
        post("skip", f.maier(), Map.of("reason", "Krank")).statusCode(200);

        post("complete", f.maier(), null).statusCode(409);
        post("start", f.maier(), null).statusCode(409);
    }

    @Test
    void startedStopMayStillBeSkipped() {
        post("start", f.maier(), null).statusCode(200);
        post("skip", f.maier(), Map.of("reason", "Kunde musste weg")).statusCode(200).body("status",
                is("UEBERSPRUNGEN"));
    }

    @Test
    void noTransitionsOnFinishedTour() {
        testData.setTourStatus(f.ownTour(), TourStatus.COMPLETED);

        post("start", f.huber(), null).statusCode(409);
        post("complete", f.huber(), null).statusCode(409);
        post("skip", f.huber(), Map.of("reason", "x")).statusCode(409);
    }

    @Test
    void customerNotOnTourIsNotFound() {
        post("start", f.ohneKoordinaten(), null).statusCode(404);
    }

    @Test
    void stopsOfColleagueTourAreNotFound() {
        given().contentType("application/json").when()
                .post("/tours/{id}/stops/{c}/start", f.colleagueTour(), f.huber()).then().statusCode(404);
    }

    @Test
    void removeOpenStopFromPlannedTourIsIdempotent() {
        given().when().delete("/tours/{id}/stops/{c}", f.ownTour(), f.huber()).then().statusCode(204);
        given().when().delete("/tours/{id}/stops/{c}", f.ownTour(), f.huber()).then().statusCode(204);

        given().when().get("/tours/{id}", f.ownTour()).then().body("customers.customerId", contains((int) f.maier()))
                .body("customers.position", contains(1));
    }

    @Test
    void startedStopCannotBeRemoved() {
        post("start", f.huber(), null).statusCode(200);

        // Tour ist durch den Start in_progress
        given().when().delete("/tours/{id}/stops/{c}", f.ownTour(), f.huber()).then().statusCode(409);
        int count = given().when().get("/tours/{id}", f.ownTour()).then().extract().path("customers.size()");
        assertEquals(2, count);
    }
}
