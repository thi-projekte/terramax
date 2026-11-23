package de.thi.terramax.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import de.thi.terramax.TestData;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class MeResourceTest {

    @Inject
    TestData testData;

    private TestData.Fixture fixture;

    @BeforeEach
    void setUp() {
        fixture = testData.reset();
    }

    @Test
    @TestSecurity(user = "sales", roles = { Roles.SALES_PROFESSIONAL, "offline_access" })
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void salesSeesDealerRolesAndEmployee() {
        given().when().get("/me").then().statusCode(200).body("username", is("sales"))
                .body("subject", is(TestData.SALES_SUB)).body("roles", contains(Roles.SALES_PROFESSIONAL))
                .body("dealerId", is(TestData.DEALER)).body("dealer.name", is("Landtechnik Wels GmbH"))
                .body("employeeId", is((int) fixture.salesId()));
    }

    @Test
    @TestSecurity(user = "testuser")
    void userWithoutRoleAndDealerMayCallMe() {
        given().when().get("/me").then().statusCode(200).body("username", is("testuser")).body("roles", empty())
                .body("dealerId", nullValue()).body("dealer", nullValue()).body("employeeId", nullValue());
    }

    @Test
    @TestSecurity(user = "other", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.OTHER_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void employeeOfAnotherDealerIsNotLinked() {
        given().when().get("/me").then().statusCode(200).body("dealerId", is(TestData.DEALER))
                .body("employeeId", nullValue());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER) })
    void dealersMeReturnsOwnDealer() {
        given().when().get("/dealers/me").then().statusCode(200).body("id", is(TestData.DEALER))
                .body("name", is("Landtechnik Wels GmbH")).body("locationId", nullValue());
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = "D-999") })
    void dealersMeWithUnknownDealerReturnsNotFound() {
        given().when().get("/dealers/me").then().statusCode(404).body("error", is("Unbekannter Händler: D-999"));
    }
}
