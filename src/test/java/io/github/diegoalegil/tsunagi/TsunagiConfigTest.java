package io.github.diegoalegil.tsunagi;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsunagiConfigTest {

    @Test
    void requestTimeoutHasASensibleDefault() {
        assertEquals(Duration.ofSeconds(30), TsunagiConfig.builder().build().requestTimeout());
    }

    @Test
    void requestTimeoutIsConfigurable() {
        TsunagiConfig config = TsunagiConfig.builder()
                .requestTimeout(Duration.ofSeconds(5))
                .build();

        assertEquals(Duration.ofSeconds(5), config.requestTimeout());
    }

    @Test
    void tmdbTokenIsEmptyWhenNotSet() {
        TsunagiConfig config = TsunagiConfig.builder().build();

        assertTrue(config.tmdbToken().isEmpty());
    }

    @Test
    void tmdbTokenIsPresentWhenSet() {
        TsunagiConfig config = TsunagiConfig.builder()
                .tmdbToken("secret-token")
                .build();

        assertTrue(config.tmdbToken().isPresent());
        assertEquals("secret-token", config.tmdbToken().get());
    }
}
