package de.thi.terramax.location;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

class MyLivenessCheckTest {

    @Test
    void reportsUp() {
        HealthCheckResponse response = new MyLivenessCheck().call();

        assertEquals("alive", response.getName());
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
    }
}
