# Changelog

All notable changes to Tsunagi are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html):
`MAJOR.MINOR.PATCH` — breaking changes bump MAJOR, backwards-compatible features
bump MINOR, and fixes bump PATCH. As of 1.0.0 the public API is considered stable.

## [Unreleased]

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

[Unreleased]: https://github.com/diegoalegil/tsunagi/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/diegoalegil/tsunagi/releases/tag/v1.0.0
