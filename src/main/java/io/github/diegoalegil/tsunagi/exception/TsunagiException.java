package io.github.diegoalegil.tsunagi.exception;

/**
 * Base type for every error raised by Tsunagi.
 *
 * <p>It is unchecked so callers are not forced to wrap every call in a
 * try/catch, while still being able to catch {@code TsunagiException} to handle
 * any library failure in one place. More specific subtypes
 * ({@link ApiException}, {@link RateLimitException},
 * {@link SourceUnavailableException}) allow finer-grained handling.
 */
public class TsunagiException extends RuntimeException {

    public TsunagiException(String message) {
        super(message);
    }

    public TsunagiException(String message, Throwable cause) {
        super(message, cause);
    }
}
