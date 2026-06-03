package io.github.diegoalegil.tsunagi.http;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpDefaultsTest {

    @Test
    void newClientHasAConnectTimeout() {
        assertEquals(HttpDefaults.CONNECT_TIMEOUT, HttpDefaults.newClient().connectTimeout().orElseThrow());
    }

    @Test
    void validateRequestTimeoutAcceptsPositiveDurations() {
        Duration ten = Duration.ofSeconds(10);
        assertEquals(ten, HttpDefaults.validateRequestTimeout(ten));
    }

    @Test
    void validateRequestTimeoutRejectsInvalidDurations() {
        assertThrows(IllegalArgumentException.class, () -> HttpDefaults.validateRequestTimeout(null));
        assertThrows(IllegalArgumentException.class, () -> HttpDefaults.validateRequestTimeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> HttpDefaults.validateRequestTimeout(Duration.ofSeconds(-1)));
    }

    @Test
    void defaultsAreSensible() {
        assertNotNull(HttpDefaults.CONNECT_TIMEOUT);
        assertEquals(Duration.ofSeconds(30), HttpDefaults.REQUEST_TIMEOUT);
    }
}
