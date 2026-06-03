package io.github.diegoalegil.tsunagi.exception;

/**
 * Thrown when a data source rejects a request because the rate limit was
 * exceeded (HTTP 429).
 */
public class RateLimitException extends ApiException {

    public RateLimitException(String source) {
        super(source, 429);
    }
}
