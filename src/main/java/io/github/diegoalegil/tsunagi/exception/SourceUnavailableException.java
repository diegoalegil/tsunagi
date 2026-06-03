package io.github.diegoalegil.tsunagi.exception;

/**
 * Thrown when a data source cannot be reached at all: a network failure, a
 * timeout, or the calling thread being interrupted while waiting. The original
 * cause is preserved.
 */
public class SourceUnavailableException extends TsunagiException {

    private final String source;

    public SourceUnavailableException(String source, String reason, Throwable cause) {
        super(source + " is unavailable: " + reason, cause);
        this.source = source;
    }

    /** The source that could not be reached. */
    public String source() {
        return source;
    }
}
