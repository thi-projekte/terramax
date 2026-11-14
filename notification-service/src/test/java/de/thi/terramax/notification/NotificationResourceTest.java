package de.thi.terramax.notification;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class NotificationResourceTest {

    @Test
    @TestSecurity(user = "alice")
    void loggedInUserGetsNotifications() {
        given().when().get("/notifications")
                .then().statusCode(200);
    }

    @Test
    void withoutTokenIsUnauthorized() {
        given().when().get("/notifications")
                .then().statusCode(401);
    }

    @Test
    void healthIsReachableWithoutToken() {
        given().when().get("/q/health")
                .then().statusCode(200)
                .body("status", is("UP"));
    }
}
