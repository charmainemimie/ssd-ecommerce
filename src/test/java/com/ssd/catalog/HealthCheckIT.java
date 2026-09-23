package com.ssd.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * T-01 "Done when": the app starts and the health check returns OK.
 * Boots the real application on a random port (not a mocked MVC slice) so
 * the check covers the same HTTP path a load balancer would hit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthCheckIT {

    @LocalServerPort
    private int port;

    @Test
    @SuppressWarnings("rawtypes")
    void T01_healthCheckReturnsUp() {
        // Plain RestClient (part of Spring MVC) rather than Boot's TestRestTemplate,
        // which in Boot 4 lives in a separate module we don't otherwise need.
        ResponseEntity<Map> response = RestClient.create("http://localhost:" + port)
                .get().uri("/actuator/health")
                .retrieve()
                .toEntity(Map.class);

        // Contract from spec Part 2 §1 "Operational endpoints": 200 + status UP.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }
}
