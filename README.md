<div align="center">

<img src="img/banner.png" alt="Tsunagi — Anime Data SDK for Java" width="100%">

# Tsunagi 繋ぎ

**Anime Data SDK for Java.** One client for [AniList](https://anilist.co), [TMDb](https://www.themoviedb.org) and [Jikan](https://jikan.moe) — three very different APIs behind a single, unified model.

[![CI](https://github.com/diegoalegil/tsunagi/actions/workflows/ci.yml/badge.svg)](https://github.com/diegoalegil/tsunagi/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.diegoalegil/tsunagi.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.diegoalegil/tsunagi)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)

</div>

---

## Why Tsunagi?

Each anime API speaks its own language:

| Source | Protocol | Auth | Quirks |
|--------|----------|------|--------|
| **AniList** | GraphQL | none | titles in 3 languages, `null` everywhere, 404 on "not found" |
| **TMDb** | REST | Bearer token | TV-oriented, scores 0–10, posters as relative paths |
| **Jikan** | REST | none | strict 3 requests/second limit, scores 0–10 |

Learning all three — GraphQL queries, Bearer headers, rate limits, three JSON shapes — just to show an anime card is a lot. **Tsunagi hides all of it** behind one call and gives you back a single, consistent [`Anime`](src/main/java/io/github/diegoalegil/tsunagi/model/Anime.java):

```
three APIs  ──►  Tsunagi  ──►  one Anime model
```

> **Tsunagi** (繋ぎ) means *connection*, *link*, *the piece that joins things together*.

## Installation

```xml
<dependency>
    <groupId>io.github.diegoalegil</groupId>
    <artifactId>tsunagi</artifactId>
    <version>0.1.0</version>
</dependency>
```

Requires **Java 21+**.

## Quick start

```java
import io.github.diegoalegil.tsunagi.TsunagiClient;
import io.github.diegoalegil.tsunagi.TsunagiConfig;
import io.github.diegoalegil.tsunagi.model.Anime;

import java.util.Optional;

TsunagiConfig config = TsunagiConfig.builder()
        .tmdbToken(System.getenv("TMDB_TOKEN")) // optional
        .cacheEnabled(true)
        .build();

TsunagiClient tsunagi = new TsunagiClient(config);

Optional<Anime> result = tsunagi.searchAnime("Cowboy Bebop");

result.ifPresent(anime -> {
    System.out.println(anime.title());        // Cowboy Bebop
    System.out.println(anime.year());         // 1998
    System.out.println(anime.averageScore()); // 86.0  (always on a 0–100 scale)
    System.out.println(anime.imageUrl());
});
```

The result is always the same `Anime`, no matter which source answered:

```java
public record Anime(
        String id,            // e.g. "anilist:1"
        String title,
        Integer year,
        String description,
        String imageUrl,
        Double averageScore   // normalised to 0–100 across all sources
) {}
```

## How it works

`searchAnime` orchestrates the three sources so you don't have to:

1. **Cache** — if enabled and the title was looked up recently, return it immediately.
2. **AniList** is queried first as the primary source.
3. **TMDb** fills in any missing fields (e.g. a poster) when a token is configured — *best-effort*: a TMDb failure never fails your search.
4. **Jikan** is the fallback when AniList has no match.

Scores from every source are normalised to a **0–100** scale, so `averageScore` always means the same thing.

## Features

- **Unified model** — one `Anime` record for all three sources.
- **Rate limiting** — a built-in token-bucket limiter keeps Jikan within its 3 req/s limit.
- **Retries** — transient failures (network, 5xx, 429) are retried with exponential backoff; client errors fail fast.
- **Caching** — optional in-memory cache with a configurable TTL.
- **Clean errors** — everything throws `TsunagiException` (or a subtype); no leaking `java.io` checked exceptions.
- **No heavy dependencies** — just Jackson and the JDK's own HTTP client.

## Configuration

Everything is set through the `TsunagiConfig` builder:

```java
TsunagiConfig config = TsunagiConfig.builder()
        .tmdbToken(System.getenv("TMDB_TOKEN")) // enables TMDb enrichment (optional)
        .cacheEnabled(true)                     // default: false
        .cacheTtl(Duration.ofMinutes(10))       // default: 10 minutes
        .retryEnabled(true)                     // default: true
        .retryMaxAttempts(3)                    // default: 3
        .retryInitialDelay(Duration.ofMillis(500)) // default: 500 ms
        .build();
```

## Error handling

All failures are unchecked and rooted at `TsunagiException`, so you only catch what you care about:

```java
try {
    Optional<Anime> anime = tsunagi.searchAnime("Frieren");
} catch (RateLimitException e) {
    // a source returned HTTP 429
} catch (ApiException e) {
    System.err.println(e.source() + " failed with " + e.statusCode());
} catch (TsunagiException e) {
    // any other Tsunagi failure (network, parsing, ...)
}
```

| Exception | Meaning |
|-----------|---------|
| `TsunagiException` | base type for every Tsunagi error |
| `ApiException` | a source returned a non-2xx status (`source()`, `statusCode()`) |
| `RateLimitException` | a source returned HTTP 429 |
| `SourceUnavailableException` | network failure, timeout or interruption |

## Using the sources directly

The individual clients are public too, if you only want one:

```java
AniListClient anilist = new AniListClient();
Optional<Anime> anime = anilist.searchAnime("Naruto");

JikanClient jikan = new JikanClient(); // owns a 3 req/s rate limiter
TmdbClient tmdb = new TmdbClient(System.getenv("TMDB_TOKEN"));
```

## Building from source

```bash
mvn test          # run the test suite (no network required)
mvn install       # install 0.1.0 into your local ~/.m2 repository
```

The test suite never calls the real APIs: HTTP responses are mapped from canned
JSON and time-based components use injectable clocks, so it is fast and stable.

## Roadmap

- [x] Unified `Anime` model
- [x] AniList, TMDb and Jikan clients
- [x] Token-bucket rate limiting
- [x] Retries with exponential backoff
- [x] In-memory caching
- [x] Unified `TsunagiClient` facade
- [x] Maven Central publishing setup
- [ ] First release on Maven Central
- [ ] Optional VS Code extension

## License

[MIT](LICENSE) © Diego Gil
