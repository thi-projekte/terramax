package de.thi.terramax.notification;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class NotificationResourceTest {

    @Inject
    NotificationStore store;

    @Test
    @TestSecurity(user = "alice")
    @OidcSecurity(claims = @Claim(key = "sub", value = "user-alice"))
    void loggedInUserGetsOnlyOwnNotifications() {
        store.add("user-alice", new Notification("n-1", "test.type", Instant.now(), "tour-1", Map.of()));
        store.add("user-bob", new Notification("n-2", "test.type", Instant.now(), "tour-2", Map.of()));

        given().when().get("/notifications")
                .then().statusCode(200)
                .body("size()", is(1))
                .body("[0].id", is("n-1"));
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
