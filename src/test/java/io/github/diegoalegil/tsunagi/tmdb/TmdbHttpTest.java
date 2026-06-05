package io.github.diegoalegil.tsunagi.tmdb;

import io.github.diegoalegil.tsunagi.exception.ApiException;
import io.github.diegoalegil.tsunagi.exception.RateLimitException;
import io.github.diegoalegil.tsunagi.exception.SourceUnavailableException;
import io.github.diegoalegil.tsunagi.exception.TsunagiException;
import io.github.diegoalegil.tsunagi.http.RetryPolicy;
import io.github.diegoalegil.tsunagi.model.Anime;
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
 * Exercises the real HTTP cycle of {@link TmdbClient} against a local
 * {@link MockWebServer}: URL building, status-code handling, malformed bodies,
 * timeouts and retry behaviour — things canned-JSON parser tests cannot cover.
 */
class TmdbHttpTest {

    private MockWebServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        // apiBase has no trailing slash; the client appends "/search/tv" etc.
        baseUrl = server.url("/").toString().replaceAll("/$", "");
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    private TmdbClient client() {
        return client(null);
    }

    private TmdbClient client(RetryPolicy retryPolicy) {
        return new TmdbClient("test-token", Duration.ofSeconds(5), null, retryPolicy, null, baseUrl);
    }

    @Test
    void searchMultiBuildsExpectedPathAndQuery() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{ \"results\": [] }"));

        client().searchMulti("Suzume", "es-ES");

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("/search/multi?query=Suzume&include_adult=false&language=es-ES", request.getPath());
        assertEquals("Bearer test-token", request.getHeader("Authorization"));
    }

    @Test
    void searchAnimeMapsOverTheWireAndSendsBearerToken() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("""
                { "results": [ { "id": 30991, "name": "Cowboy Bebop", "first_air_date": "1998-04-03" } ] }
                """));

        Optional<Anime> result = client().searchAnime("Cowboy Bebop");

        assertTrue(result.isPresent());
        assertEquals("tmdb:30991", result.get().id());
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("/search/tv?query=Cowboy+Bebop", request.getPath());
        assertEquals("Bearer test-token", request.getHeader("Authorization"));
    }

    @Test
    void throwsRateLimitOn429() {
        server.enqueue(new MockResponse().setResponseCode(429));

        assertThrows(RateLimitException.class, () -> client().searchTv("x", null));
    }

    @Test
    void throwsApiExceptionWithStatusOn500() {
        server.enqueue(new MockResponse().setResponseCode(500));

        ApiException ex = assertThrows(ApiException.class, () -> client().searchTv("x", null));
        assertEquals(500, ex.statusCode());
    }

    @Test
    void throwsTsunagiExceptionOnMalformedJson() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{ this is not json"));

        assertThrows(TsunagiException.class, () -> client().searchTv("x", null));
    }

    @Test
    void retriesTransient503ThenSucceeds() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{ \"results\": [] }"));

        // 5xx is transient: with two attempts the second (200) succeeds.
        RetryPolicy retry = RetryPolicy.exponentialBackoff(2, Duration.ofMillis(1));
        TmdbSearchResponse response = client(retry).searchTv("x", null);

        assertTrue(response.results().isEmpty());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void throwsSourceUnavailableOnTimeout() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{ \"results\": [] }")
                .setHeadersDelay(2, TimeUnit.SECONDS));

        // Request timeout (200ms) is far below the server's 2s delay → times out.
        TmdbClient slow = new TmdbClient("test-token", Duration.ofMillis(200), null, null, null, baseUrl);
        assertThrows(SourceUnavailableException.class, () -> slow.searchTv("x", null));
    }
}
