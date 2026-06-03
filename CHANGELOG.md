# Changelog

All notable changes to Tsunagi are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html):
`MAJOR.MINOR.PATCH` — breaking changes bump MAJOR, backwards-compatible features
bump MINOR, and fixes bump PATCH. As of 1.0.0 the public API is considered stable.

## [Unreleased]

## [1.2.0] - 2026-06-03

Additive release: richer search and title metadata, aimed at cross-source title
matching (TV vs movie, native titles, synonyms).

### Added
- `TmdbClient.searchMulti(query, language)` — searches `/search/multi` (TV **and**
  movies). `TmdbSearchResult` now also carries `title`, `originalTitle`,
  `releaseDate`, `mediaType` and `originalLanguage`, plus `displayName()`,
  `displayOriginalName()`, `displayDate()`, `isTv()` and `isMovie()` helpers so TV
  and movie results read uniformly.
- `AniListTitle.nativeTitle()` (the native-language title) and
  `AniListMedia.synonyms()` (alternative titles), both populated by `fetchPopular`.

## [1.1.0] - 2026-06-03

Additive, backwards-compatible release: the individual source clients gain
general-purpose capabilities (paginated popular fetch and rich AniList/TMDb
models). The `TsunagiClient` facade, the `Anime` model and `searchAnime` keep
their existing behaviour (they only propagate the new User-Agent).

### Added
- `AniListClient.fetchPopular(int)` — fetches the most popular anime, paginating
  internally (AniList caps a page at 50), plus the rich AniList models it returns:
  `AniListMedia` with `AniListTitle`, `AniListFuzzyDate` (start/end), studios
  (`AniListStudioConnection`/`AniListStudio`, incl. `isAnimationStudio`), up to
  six main characters (`AniListCharacterConnection`/`AniListCharacterEdge`/
  `AniListCharacter`/`AniListCharacterName`/`AniListCharacterImage`), tags
  (`AniListTag` with `rank`), `coverImage`, `bannerImage`, `season`/`seasonYear`,
  `popularity` and more.
- TMDb endpoints `TmdbClient.searchTv(query, language)`, `getTvDetails(id,
  language)`, `getWatchProviders(id)` and `getTrailers(id, language)`, with the
  records `TmdbSearchResponse`/`TmdbSearchResult`, `TmdbTvDetailsResponse`,
  `TmdbProvidersResponse`/`TmdbCountryProviders`/`TmdbProvider` (flatrate, free,
  rent and buy) and `TmdbVideosResponse`/`TmdbVideo`. Language is always a
  caller-supplied parameter; logo and poster paths are returned raw.
- `TsunagiConfig.userAgent(String)` and a matching `Optional<String> userAgent()`
  getter; the configured User-Agent is propagated to `AniListClient` and
  `TmdbClient`. Sending a `User-Agent` requires running the JVM with
  `-Djdk.httpclient.allowRestrictedHeaders=user-agent`, so it is opt-in.
- Optional per-client retry (`RetryPolicy`) and rate limiting
  (`TokenBucketRateLimiter`) on the new AniList/TMDb fetch paths.

## [1.0.0] - 2026-06-03

First stable release. The public API (`TsunagiClient`, `TsunagiConfig`, `Anime`
and the `TsunagiException` hierarchy) is now considered stable.

### Added
- Unified `Anime` model with `id`, `title`, `year`, `description`, `imageUrl`,
  `averageScore` (normalised 0–100), `genres` (never null), `episodes`, `status`
  and `source`.
- `TsunagiClient` facade orchestrating AniList (primary) → TMDb (best-effort
  enrichment) → Jikan (fallback), returning a unified `Anime`.
- `TsunagiConfig` builder: optional TMDb token, cache (enabled, TTL, max size),
  retries (enabled, max attempts, initial delay) and request timeout.
- Source clients: `AniListClient` (GraphQL, query variables), `TmdbClient`
  (Bearer auth), `JikanClient` (rate-limited).
- `TokenBucketRateLimiter` — thread-safe token bucket (e.g. Jikan's 3 req/s).
- `RetryPolicy` — exponential backoff for transient failures only (network, 5xx,
  429), capped at 60s; client errors fail fast.
- `MemoryCache` — thread-safe TTL cache with bounded LRU eviction.
- Exception hierarchy: `TsunagiException`, `ApiException`, `RateLimitException`,
  `SourceUnavailableException`.
- HTTP connect/request timeouts on every client.
- Input validation: null/blank search titles are rejected with a clear error.
- Maven Central publishing setup (release profile, signing, Central Portal) and
  GitHub Actions CI.

[Unreleased]: https://github.com/diegoalegil/tsunagi/compare/v1.2.0...HEAD
[1.2.0]: https://github.com/diegoalegil/tsunagi/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/diegoalegil/tsunagi/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/diegoalegil/tsunagi/releases/tag/v1.0.0
