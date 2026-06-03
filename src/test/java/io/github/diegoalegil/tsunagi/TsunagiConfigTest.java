package io.github.diegoalegil.tsunagi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsunagiConfigTest {

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
