package io.github.diegoalegil.tsunagi.jikan;

import io.github.diegoalegil.tsunagi.exception.ApiException;
import io.github.diegoalegil.tsunagi.exception.RateLimitException;
import io.github.diegoalegil.tsunagi.http.RetryPolicy;
import io.github.diegoalegil.tsunagi.model.Anime;
import io.github.diegoalegil.tsunagi.ratelimit.TokenBucketRateLimiter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real HTTP cycle of {@link JikanClient} against a local
 * {@link MockWebServer}: over-the-wire mapping, status-code handling and the
 * newly added retry behaviour.
 */
class JikanHttpTest {

    private MockWebServer server;
    private String searchUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        searchUrl = server.url("/v4/anime").toString();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    private JikanClient client(RetryPolicy retryPolicy) {
        // A generous limiter keeps the test fast while still honouring the pattern.
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(100, Duration.ofSeconds(1));
        return new JikanClient(limiter, retryPolicy, Duration.ofSeconds(5), searchUrl);
    }

    @Test
    void searchAnimeMapsOverTheWire() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("""
                { "data": [ { "mal_id": 1, "title": "Cowboy Bebop", "year": 1998 } ] }
                """));

        Optional<Anime> result = client(null).searchAnime("Cowboy Bebop");

        assertTrue(result.isPresent());
        assertEquals("jikan:1", result.get().id());
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("/v4/anime?q=Cowboy+Bebop&limit=1", request.getPath());
    }

    @Test
    void throwsRateLimitOn429() {
        server.enqueue(new MockResponse().setResponseCode(429));

        assertThrows(RateLimitException.class, () -> client(null).searchAnime("x"));
    }

    @Test
    void throwsApiExceptionWithStatusOn500() {
        server.enqueue(new MockResponse().setResponseCode(500));

        ApiException ex = assertThrows(ApiException.class, () -> client(null).searchAnime("x"));
        assertEquals(500, ex.statusCode());
    }

    @Test
    void retriesTransient503ThenSucceeds() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("""
                { "data": [ { "mal_id": 7, "title": "Old Show" } ] }
                """));

        RetryPolicy retry = RetryPolicy.exponentialBackoff(2, Duration.ofMillis(1));
        Optional<Anime> result = client(retry).searchAnime("x");

        assertTrue(result.isPresent());
        assertEquals("jikan:7", result.get().id());
        assertEquals(2, server.getRequestCount());
    }
}
