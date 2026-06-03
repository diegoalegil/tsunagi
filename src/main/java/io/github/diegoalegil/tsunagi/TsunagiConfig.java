package io.github.diegoalegil.tsunagi;

import java.util.Optional;

/**
 * Immutable configuration for {@link TsunagiClient}, created through a builder.
 *
 * <pre>{@code
 * TsunagiConfig config = TsunagiConfig.builder()
 *         .tmdbToken(System.getenv("TMDB_TOKEN"))
 *         .build();
 * }</pre>
 *
 * <p>The TMDb token is optional: when it is absent, Tsunagi simply skips the
 * TMDb enrichment step and relies on AniList and Jikan.
 */
public final class TsunagiConfig {

    private final String tmdbToken;

    private TsunagiConfig(Builder builder) {
        this.tmdbToken = builder.tmdbToken;
    }

    /** The TMDb Bearer token, if one was configured. */
    public Optional<String> tmdbToken() {
        return Optional.ofNullable(tmdbToken);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String tmdbToken;

        private Builder() {
        }

        /** Sets the TMDb Bearer token used for enrichment. Optional. */
        public Builder tmdbToken(String tmdbToken) {
            this.tmdbToken = tmdbToken;
            return this;
        }

        public TsunagiConfig build() {
            return new TsunagiConfig(this);
        }
    }
}
