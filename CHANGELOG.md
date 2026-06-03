# Changelog

All notable changes to Tsunagi are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html):
`MAJOR.MINOR.PATCH` — breaking changes bump MAJOR, backwards-compatible features
bump MINOR, and fixes bump PATCH. Versions below 1.0.0 may still change the API.

## [Unreleased]

## [0.1.0] - 2026-06-03

First public release.

### Added
- Unified `Anime` model shared by every source.
- `AniListClient` — GraphQL client using query variables, returning `Optional<Anime>`.
- `TmdbClient` — REST client with Bearer-token auth, posters and 0–100 score normalisation.
- `JikanClient` — REST client wired to a rate limiter, with score and year fallbacks.
- `TokenBucketRateLimiter` — thread-safe token-bucket limiter (e.g. Jikan's 3 req/s).
- `RetryPolicy` — exponential backoff that retries only transient failures.
- `MemoryCache` — thread-safe in-memory cache with per-entry TTL.
- `TsunagiClient` + `TsunagiConfig` — unified facade orchestrating AniList, TMDb and Jikan,
  with optional caching and retries.
- `TsunagiException` hierarchy (`ApiException`, `RateLimitException`, `SourceUnavailableException`).
- Maven Central publishing setup (release profile, signing, Central Portal) and GitHub Actions CI.

[Unreleased]: https://github.com/diegoalegil/tsunagi/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/diegoalegil/tsunagi/releases/tag/v0.1.0
