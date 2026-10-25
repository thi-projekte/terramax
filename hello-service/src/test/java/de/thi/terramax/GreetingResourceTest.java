package de.thi.terramax;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class GreetingResourceTest {

    @Test
    @TestSecurity(user = "alice")
    void helloWithUserReturnsGreeting() {
        given()
                .when().get("/hello")
                .then()
                .statusCode(200)
                .body("message", is("Hello alice"));
    }

    @Test
    void helloWithoutTokenReturnsUnauthorized() {
        given()
                .when().get("/hello")
                .then()
                .statusCode(401);
    }

    @Test
    void helloWithInvalidTokenReturnsUnauthorized() {
        given()
                .header("Authorization", "Bearer invalid-token")
                .when().get("/hello")
                .then()
                .statusCode(401);
    }

    @Test
    void healthWithoutTokenReturnsUp() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }
}