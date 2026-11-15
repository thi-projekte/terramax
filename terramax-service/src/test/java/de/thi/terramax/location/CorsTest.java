package de.thi.terramax.location;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

/** Prüft die CORS-Konfiguration (Standard-Origin http://localhost:5173 aus application.properties). */
@QuarkusTest
class CorsTest {

    private static final String FRONTEND_ORIGIN = "http://localhost:5173";
    private static final String FOREIGN_ORIGIN = "https://evil.example";

    @Test
    void preflightFromFrontendOriginIsAllowed() {
        given().header("Origin", FRONTEND_ORIGIN).header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization,content-type").when()
                .options("/locations").then().statusCode(200)
                .header("Access-Control-Allow-Origin", is(FRONTEND_ORIGIN))
                .header("Access-Control-Allow-Methods", containsString("GET"))
                .header("Access-Control-Allow-Methods", not(containsString("DELETE")))
                .header("Access-Control-Allow-Headers", containsString("Authorization"))
                .header("Access-Control-Allow-Headers", containsString("Content-Type"));
    }

    @Test
    void preflightFromForeignOriginIsRejected() {
        given().header("Origin", FOREIGN_ORIGIN).header("Access-Control-Request-Method", "GET").when()
                .options("/locations").then().statusCode(403).header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    @TestSecurity(user = "alice")
    void getFromFrontendOriginReturnsAllowOriginHeader() {
        given().header("Origin", FRONTEND_ORIGIN).when().get("/locations/res-21").then().statusCode(200)
                .header("Access-Control-Allow-Origin", is(FRONTEND_ORIGIN));
    }
}
