package de.thi.terramax;

import static io.restassured.RestAssured.given;

import de.thi.terramax.security.Roles;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Zugriffsschutz aller Händler-Endpunkte: ohne Token 401, ohne TerraMax-Rolle 403, ohne dealer_id 403.
 */
@QuarkusTest
class SecurityTest {

    private static final String BODY = "{}";

    @ParameterizedTest
    @CsvSource({ "GET,/me", "GET,/dealers/me", "GET,/customers", "GET,/customers/1", "POST,/customers",
            "PUT,/customers/1", "GET,/customers/1/protocols", "GET,/tours", "GET,/tours/1", "POST,/tours",
            "PATCH,/tours/1", "GET,/tours/1/protocols", "POST,/tours/1/protocols" })
    void withoutTokenReturnsUnauthorized(String method, String path) {
        given().contentType("application/json").body(BODY).when().request(method, path).then().statusCode(401);
    }

    /** Läuft gegen den echten OIDC-Mechanismus (Dev-Services-Keycloak), daher ohne @TestSecurity. */
    @ParameterizedTest
    @CsvSource({ "GET,/me", "GET,/customers", "GET,/tours" })
    void withInvalidTokenReturnsUnauthorized(String method, String path) {
        given().header("Authorization", "Bearer abc.def.ghi").when().request(method, path).then().statusCode(401);
    }

    @ParameterizedTest
    @CsvSource({ "GET,/dealers/me", "GET,/customers", "GET,/customers/1", "POST,/customers", "PUT,/customers/1",
            "GET,/customers/1/protocols", "GET,/tours", "GET,/tours/1", "POST,/tours", "PATCH,/tours/1",
            "GET,/tours/1/protocols", "POST,/tours/1/protocols" })
    @TestSecurity(user = "testuser")
    @OidcSecurity(claims = @Claim(key = "dealer_id", value = TestData.DEALER))
    void withoutRoleReturnsForbidden(String method, String path) {
        given().contentType("application/json").body(BODY).when().request(method, path).then().statusCode(403);
    }

    @ParameterizedTest
    @CsvSource({ "GET,/dealers/me", "GET,/customers", "GET,/customers/1", "POST,/customers", "PUT,/customers/1",
            "GET,/customers/1/protocols", "GET,/tours", "GET,/tours/1", "POST,/tours", "PATCH,/tours/1",
            "GET,/tours/1/protocols", "POST,/tours/1/protocols" })
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = @Claim(key = "sub", value = TestData.SALES_SUB))
    void withoutDealerIdReturnsForbidden(String method, String path) {
        given().contentType("application/json").body(BODY).when().request(method, path).then().statusCode(403);
    }
}
