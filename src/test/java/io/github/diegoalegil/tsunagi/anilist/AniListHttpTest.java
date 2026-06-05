package io.github.diegoalegil.tsunagi.anilist;

import io.github.diegoalegil.tsunagi.exception.RateLimitException;
import io.github.diegoalegil.tsunagi.http.RetryPolicy;
import io.github.diegoalegil.tsunagi.model.Anime;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real HTTP cycle of {@link AniListClient} against a local
 * {@link MockWebServer}: over-the-wire mapping, the HTTP-200-with-errors contract,
 * pagination and retry behaviour.
 */
class AniListHttpTest {

    private MockWebServer server;
    private URI apiUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        apiUrl = URI.create(server.url("/").toString());
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    private AniListClient client(RetryPolicy retryPolicy) {
        return new AniListClient(Duration.ofSeconds(5), null, retryPolicy, null, apiUrl);
    }

    @Test
    void searchAnimeMapsOverTheWireWithJsonPost() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("""
                { "data": { "Media": { "id": 1, "title": { "romaji": "Cowboy Bebop" } } } }
                """));

        Optional<Anime> result = client(null).searchAnime("Cowboy Bebop");

        assertTrue(result.isPresent());
        assertEquals("anilist:1", result.get().id());
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("POST", request.getMethod());
        assertEquals("application/json", request.getHeader("Content-Type"));
    }

    @Test
    void throwsRateLimitWhenServerReturns200WithErrors() {
        // The crux of the resilience fix: AniList answers HTTP 200 with an errors
        // array when rate-limited. End to end this must surface as RateLimitException.
        server.enqueue(new MockResponse().setResponseCode(200).setBody("""
                { "data": null, "errors": [ { "message": "Too Many Requests.", "status": 429 } ] }
                """));

        assertThrows(RateLimitException.class, () -> client(null).searchAnime("anything"));
    }

    @Test
    void throwsRateLimitOnHttp429() {
        server.enqueue(new MockResponse().setResponseCode(429));

        assertThrows(RateLimitException.class, () -> client(null).searchAnime("anything"));
    }

    @Test
    void fetchPopularPaginatesUntilCountIsReached() throws Exception {
        // count=51 forces two pages (perPage caps at 50): a full 50-item first page
        // and a 1-item second page.
        server.enqueue(new MockResponse().setResponseCode(200).setBody(pageOf(50)));
        server.enqueue(new MockResponse().setResponseCode(200).setBody(pageOf(1)));

        List<AniListMedia> media = client(null).fetchPopular(51);

        assertEquals(51, media.size());
        assertEquals(2, server.getRequestCount());
        server.takeRequest(1, TimeUnit.SECONDS); // page 1
        RecordedRequest page2 = server.takeRequest(1, TimeUnit.SECONDS);
        assertTrue(page2.getBody().readUtf8().contains("\"page\":2"));
    }

    @Test
    void fetchPopularRetriesTransient503ThenSucceeds() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(200).setBody(pageOf(1)));

        RetryPolicy retry = RetryPolicy.exponentialBackoff(2, Duration.ofMillis(1));
        List<AniListMedia> media = client(retry).fetchPopular(1);

        assertEquals(1, media.size());
        assertEquals(2, server.getRequestCount());
    }

    /** Builds a popular-page body with {@code n} minimal media items. */
    private static String pageOf(int n) {
        StringJoiner media = new StringJoiner(",", "[", "]");
        for (int i = 1; i <= n; i++) {
            media.add("{ \"id\": " + i + ", \"title\": { \"romaji\": \"Anime " + i + "\" } }");
        }
        return "{ \"data\": { \"Page\": { \"media\": " + media + " } } }";
    }
}
