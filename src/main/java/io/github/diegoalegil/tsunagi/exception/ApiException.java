package io.github.diegoalegil.tsunagi.exception;

/**
 * Thrown when a data source responds with an unsuccessful HTTP status.
 */
public class ApiException extends TsunagiException {

    private final String source;
    private final int statusCode;

    public ApiException(String source, int statusCode) {
        super(source + " API returned status " + statusCode);
        this.source = source;
        this.statusCode = statusCode;
    }

    /** The source that failed, e.g. {@code "AniList"}, {@code "TMDb"}, {@code "Jikan"}. */
    public String source() {
        return source;
    }

    /** The HTTP status code returned by the source. */
    public int statusCode() {
        return statusCode;
    }
}
