package io.github.diegoalegil.tsunagi.exception;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExceptionTest {

    @Test
    void apiExceptionCarriesSourceAndStatus() {
        ApiException e = new ApiException("AniList", 500);

        assertEquals("AniList", e.source());
        assertEquals(500, e.statusCode());
        assertInstanceOf(TsunagiException.class, e);
    }

    @Test
    void rateLimitExceptionIsAnApiExceptionWith429() {
        RateLimitException e = new RateLimitException("Jikan");

        assertEquals("Jikan", e.source());
        assertEquals(429, e.statusCode());
        assertInstanceOf(ApiException.class, e);
    }

    @Test
    void sourceUnavailableExceptionKeepsTheOriginalCause() {
        IOException cause = new IOException("network down");
        SourceUnavailableException e = new SourceUnavailableException("TMDb", "request failed", cause);

        assertEquals("TMDb", e.source());
        assertSame(cause, e.getCause());
        assertInstanceOf(TsunagiException.class, e);
    }
}
