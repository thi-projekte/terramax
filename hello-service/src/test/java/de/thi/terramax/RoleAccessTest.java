package de.thi.terramax;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

/**
 * Prüft die Rollenregeln
 * 200 = richtige Rolle, 403 = angemeldet aber falsche/keine Rolle, 401 = nicht angemeldet.
 */
@QuarkusTest
class RoleAccessTest {

    // --- /sales ---

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void salesProfessionalCanAccessSales() {
        given().when().get("/sales")
                .then().statusCode(200)
                .body("message", is("Hello sales, du bist als Sales Professional angemeldet"));
    }

    @Test
    @TestSecurity(user = "dealer", roles = Roles.DEALER_PRINCIPAL)
    void dealerPrincipalIsForbiddenOnSales() {
        given().when().get("/sales").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "testuser")
    void userWithoutRoleIsForbiddenOnSales() {
        given().when().get("/sales").then().statusCode(403);
    }

    @Test
    void anonymousIsUnauthorizedOnSales() {
        given().when().get("/sales").then().statusCode(401);
    }

    // --- /dealer ---

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    void salesProfessionalIsForbiddenOnDealer() {
        given().when().get("/dealer").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "dealer", roles = Roles.DEALER_PRINCIPAL)
    void dealerPrincipalCanAccessDealer() {
        given().when().get("/dealer").then().statusCode(200);
    }

    // --- /me ---

    @Test
    @TestSecurity(user = "sales", roles = {Roles.SALES_PROFESSIONAL, "offline_access"})
    void meReturnsOnlyTerraMaxRoles() {
        given().when().get("/me")
                .then().statusCode(200)
                .body("username", is("sales"))
                .body("roles", contains(Roles.SALES_PROFESSIONAL));
    }

    @Test
    @TestSecurity(user = "testuser")
    void meWorksWithoutBusinessRole() {
        given().when().get("/me")
                .then().statusCode(200)
                .body("roles", empty());
    }

    @Test
    void anonymousIsUnauthorizedOnMe() {
        given().when().get("/me").then().statusCode(401);
    }
}
