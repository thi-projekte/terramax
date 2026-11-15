package de.thi.terramax.tour;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import de.thi.terramax.TestData;
import de.thi.terramax.security.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TourResourceTest {

    @Inject
    TestData testData;

    private TestData.Fixture f;

    @BeforeEach
    void setUp() {
        f = testData.reset();
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void salesSeesOnlyOwnTours() {
        given().when().get("/tours").then().statusCode(200).body("id", contains((int) f.ownTour()))
                .body("[0].customerCount", is(2)).body("[0].status", is("planned"));
    }

    @Test
    @TestSecurity(user = "dealer", roles = { Roles.DEALER_PRINCIPAL, Roles.SALES_PROFESSIONAL })
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.PRINCIPAL_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void principalSeesAllToursOfOwnDealerNewestFirst() {
        given().when().get("/tours").then().statusCode(200).body("id",
                contains((int) f.colleagueTour(), (int) f.ownTour()));
    }

    @Test
    @TestSecurity(user = "nobody", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = "sub-unknown"),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void salesWithoutEmployeeEntrySeesNoTours() {
        given().when().get("/tours").then().statusCode(200).body("$", empty());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void getOwnTourWithCustomersInOrder() {
        given().when().get("/tours/{id}", f.ownTour()).then().statusCode(200)
                .body("customers.name", contains("Hof Huber", "Hof Maier")).body("customers.position", contains(1, 2))
                .body("employeeIds", contains((int) f.salesId()));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void salesMayNotReadTourOfColleague() {
        given().when().get("/tours/{id}", f.colleagueTour()).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "dealer", roles = Roles.DEALER_PRINCIPAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.PRINCIPAL_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void principalReadsTourOfColleagueButNotOfOtherDealer() {
        given().when().get("/tours/{id}", f.colleagueTour()).then().statusCode(200);
        given().when().get("/tours/{id}", f.foreignTour()).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void createTourAssignsCurrentUser() {
        int id = given().contentType("application/json")
                .body(Map.of("tourDate", "2026-12-01", "customerIds", List.of(f.maier(), f.huber()))).when()
                .post("/tours").then().statusCode(201).body("status", is("planned"))
                .body("customers.name", contains("Hof Maier", "Hof Huber"))
                .body("employeeIds", contains((int) f.salesId())).extract().path("id");

        given().when().get("/tours").then().body("id", containsInAnyOrder(id, (int) f.ownTour()));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void createTourWithCustomerOfOtherDealerIsBadRequest() {
        given().contentType("application/json")
                .body(Map.of("tourDate", "2026-12-01", "customerIds", List.of(f.huber(), f.fremd()))).when()
                .post("/tours").then().statusCode(400).body("error", is("Unbekannter Kunde: " + f.fremd()));
    }

    @Test
    @TestSecurity(user = "dealer", roles = Roles.DEALER_PRINCIPAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.PRINCIPAL_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void principalMayNotAssignEmployeeOfOtherDealer() {
        given().contentType("application/json")
                .body(Map.of("tourDate", "2026-12-01", "employeeIds", List.of(f.otherEmployeeId()))).when()
                .post("/tours").then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "nobody", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = "sub-unknown"),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void salesWithoutEmployeeEntryMayNotCreateTour() {
        given().contentType("application/json").body(Map.of("tourDate", "2026-12-01")).when().post("/tours").then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void createTourWithoutDateIsBadRequest() {
        given().contentType("application/json").body(Map.of()).when().post("/tours").then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void patchChangesStatusOrderAndVisited() {
        given().contentType("application/json")
                .body(Map.of("status", "in_progress", "customerOrder", List.of(f.maier(), f.huber()), "visited",
                        Map.of(String.valueOf(f.maier()), true)))
                .when().patch("/tours/{id}", f.ownTour()).then().statusCode(200).body("status", is("in_progress"))
                .body("customers.name", contains("Hof Maier", "Hof Huber")).body("customers.visited",
                        contains(true, false));

        given().when().get("/tours/{id}", f.ownTour()).then().body("customers.name", contains("Hof Maier", "Hof Huber"))
                .body("customers.position", contains(1, 2));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void patchWithUnknownStatusIsBadRequest() {
        given().contentType("application/json").body(Map.of("status", "done")).when()
                .patch("/tours/{id}", f.ownTour()).then().statusCode(400).body("error", is("Unbekannter Status: done"));
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void patchWithIncompleteOrderIsBadRequest() {
        given().contentType("application/json").body(Map.of("customerOrder", List.of(f.huber()))).when()
                .patch("/tours/{id}", f.ownTour()).then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void patchTourOfColleagueIsNotFound() {
        given().contentType("application/json").body(Map.of("status", "discarded")).when()
                .patch("/tours/{id}", f.colleagueTour()).then().statusCode(404);
    }
}
