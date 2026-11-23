package de.thi.terramax.security;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.thi.terramax.TestData;
import de.thi.terramax.dealer.DealerEmployee;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Beim ersten Aufruf eines Nutzers mit dealer_id und Fachrolle entsteht genau ein Mitarbeiter-Eintrag.
 */
@QuarkusTest
class EmployeeProvisioningTest {

    private static final String NEW_SUB = "sub-new";

    @Inject
    TestData testData;

    @BeforeEach
    void setUp() {
        testData.reset();
    }

    private static long count(String sub) {
        return QuarkusTransaction.requiringNew().call(() -> DealerEmployee.count("keycloakUserId", sub));
    }

    private static DealerEmployee employee(String sub) {
        return QuarkusTransaction.requiringNew().call(() -> DealerEmployee.findByKeycloakUserId(sub).orElseThrow());
    }

    @Test
    @TestSecurity(user = "neu", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = NEW_SUB), @Claim(key = "dealer_id", value = TestData.DEALER),
            @Claim(key = "name", value = "Nora Neu") })
    void firstCallCreatesExactlyOneEmployee() {
        assertEquals(0, count(NEW_SUB));

        int first = given().when().get("/me").then().statusCode(200).extract().path("employeeId");
        int second = given().when().get("/me").then().statusCode(200).extract().path("employeeId");

        assertEquals(first, second);
        assertEquals(1, count(NEW_SUB));
        DealerEmployee e = employee(NEW_SUB);
        assertEquals("Nora Neu", e.name);
        assertEquals(Roles.SALES_PROFESSIONAL, e.role);
        assertEquals(TestData.DEALER, QuarkusTransaction.requiringNew()
                .call(() -> DealerEmployee.<DealerEmployee> findById(e.id).dealer.id));
    }

    @Test
    @TestSecurity(user = "neu", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = NEW_SUB), @Claim(key = "dealer_id", value = TestData.DEALER) })
    void anyEndpointProvisionsAndNameFallsBackToUsername() {
        given().when().get("/customers").then().statusCode(200);
        given().when().get("/tours").then().statusCode(200);

        assertEquals(1, count(NEW_SUB));
        assertEquals("neu", employee(NEW_SUB).name);
    }

    @Test
    @TestSecurity(user = "chefin", roles = { Roles.DEALER_PRINCIPAL, Roles.SALES_PROFESSIONAL })
    @OidcSecurity(claims = { @Claim(key = "sub", value = NEW_SUB), @Claim(key = "dealer_id", value = TestData.DEALER) })
    void principalGetsPrincipalRole() {
        given().when().get("/me").then().statusCode(200);
        assertEquals(Roles.DEALER_PRINCIPAL, employee(NEW_SUB).role);
    }

    @Test
    @TestSecurity(user = "sales", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = TestData.SALES_SUB),
            @Claim(key = "dealer_id", value = TestData.DEALER), @Claim(key = "name", value = "Anderer Name") })
    void existingEmployeeIsNotOverwritten() {
        given().when().get("/me").then().statusCode(200);
        assertEquals(1, count(TestData.SALES_SUB));
        assertEquals("Sam Sales", employee(TestData.SALES_SUB).name);
    }

    @Test
    @TestSecurity(user = "testuser")
    @OidcSecurity(claims = { @Claim(key = "sub", value = NEW_SUB), @Claim(key = "dealer_id", value = TestData.DEALER) })
    void userWithoutBusinessRoleIsNotProvisioned() {
        given().when().get("/me").then().statusCode(200);
        assertEquals(0, count(NEW_SUB));
    }

    @Test
    @TestSecurity(user = "neu", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = { @Claim(key = "sub", value = NEW_SUB), @Claim(key = "dealer_id", value = "D-999") })
    void unknownDealerIsNotProvisioned() {
        given().when().get("/me").then().statusCode(200);
        assertEquals(0, count(NEW_SUB));
    }

    @Test
    @TestSecurity(user = "neu", roles = Roles.SALES_PROFESSIONAL)
    @OidcSecurity(claims = @Claim(key = "sub", value = NEW_SUB))
    void userWithoutDealerIdIsNotProvisioned() {
        given().when().get("/me").then().statusCode(200);
        assertEquals(0, count(NEW_SUB));
    }
}
