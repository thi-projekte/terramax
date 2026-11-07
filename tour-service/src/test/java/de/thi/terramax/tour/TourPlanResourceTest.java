package de.thi.terramax.tour;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Prüft GET /tours/plan: Rollen (200/403/401), Antwortformat und Parameterprüfung (400).
 */
@QuarkusTest
class TourPlanResourceTest {

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void salesProfessionalGetsPlanForGivenDate() {
        given().queryParam("date", "2026-11-10").queryParam("maxVisits", 3)
                .when().get("/tours/plan")
                .then().statusCode(200)
                .body("date", is("2026-11-10"))
                .body("start.latitude", notNullValue())
                .body("visits", hasSize(3))
                .body("visits[0].position", is(1))
                .body("visits[0].customer.id", notNullValue())
                .body("visits[0].customer.name", notNullValue())
                .body("visits.distanceFromPreviousKm", everyItem(greaterThan(0f)))
                .body("totalDistanceKm", greaterThan(0f));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void usesTodayAndFiveVisitsByDefault() {
        given().when().get("/tours/plan")
                .then().statusCode(200)
                .body("date", notNullValue())
                .body("visits", hasSize(TourPlanResource.DEFAULT_VISITS));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void rejectsInvalidDate() {
        given().queryParam("date", "10.11.2026")
                .when().get("/tours/plan")
                .then().statusCode(400)
                .body("message", containsString("JJJJ-MM-TT"));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void rejectsTooFewVisits() {
        given().queryParam("maxVisits", 1)
                .when().get("/tours/plan")
                .then().statusCode(400)
                .body("message", containsString("maxVisits"));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void rejectsTooManyVisits() {
        given().queryParam("maxVisits", 11)
                .when().get("/tours/plan")
                .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "testuser")
    void userWithoutSalesRoleIsForbidden() {
        given().when().get("/tours/plan").then().statusCode(403);
    }

    @Test
    void anonymousIsUnauthorized() {
        given().when().get("/tours/plan").then().statusCode(401);
    }
}
