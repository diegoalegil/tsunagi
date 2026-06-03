package io.github.diegoalegil.tsunagi.http;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Shared HTTP defaults used by every source client: a connect timeout applied to
 * the {@link HttpClient} and a per-request timeout applied to each request, so a
 * hung connection never blocks the calling thread forever.
 */
public final class HttpDefaults {

    private HttpDefaults() {
    }

    /** Maximum time to establish a TCP connection. */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** Default maximum time to wait for a full response. */
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** Creates an {@link HttpClient} with the shared connect timeout. */
    public static HttpClient newClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /** Validates a request timeout, returning it unchanged when valid. */
    public static Duration validateRequestTimeout(Duration requestTimeout) {
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("requestTimeout must be positive, got " + requestTimeout);
        }
        return requestTimeout;
    }
}
