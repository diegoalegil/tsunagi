<div align="center">

<img src="img/logo.png" alt="Tsunagi logo" width="160">

# Tsunagi 繋ぎ

**Anime Data SDK for Java.** One client for [AniList](https://anilist.co), [TMDb](https://www.themoviedb.org) and [Jikan](https://jikan.moe) — three very different APIs behind a single, unified model.

[![CI](https://github.com/diegoalegil/tsunagi/actions/workflows/ci.yml/badge.svg)](https://github.com/diegoalegil/tsunagi/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.diegoalegil/tsunagi.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.diegoalegil/tsunagi)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://adoptium.net/)

<img src="img/banner.png" alt="Tsunagi — Anime Data SDK for Java" width="100%">

</div>

---

## What is Tsunagi?

Tsunagi is a small, dependency-light Java SDK that fetches anime data. Instead of
learning three different APIs, you make one call and get back one consistent
[`Anime`](src/main/java/io/github/diegoalegil/tsunagi/model/Anime.java) object.

```
three APIs  ──►  Tsunagi  ──►  one Anime model
```

> **Tsunagi** (繋ぎ) means *connection*, *link*, *the piece that joins things together*.

### The problem it solves

Each anime API speaks its own language:

| Source | Protocol | Auth | Quirks |
|--------|----------|------|--------|
| **AniList** | GraphQL | none | titles in 3 languages, `null` everywhere, 404 on "not found" |
| **TMDb** | REST | Bearer token | TV-oriented, scores 0–10, posters as relative paths |
| **Jikan** | REST | none | strict 3 requests/second limit, scores 0–10 |

Learning all three — GraphQL queries, Bearer headers, rate limits, three JSON
shapes — just to show an anime card is a lot. Tsunagi hides all of it and also
handles rate limiting, retries, caching and timeouts for you.

## Installation

```xml
<dependency>
    <groupId>io.github.diegoalegil</groupId>
    <artifactId>tsunagi</artifactId>
    <version>1.1.0</version>
</dependency>
```

Requires **Java 17+**.

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

Optional<Anime> result = tsunagi.searchAnime("Frieren");

result.ifPresent(anime -> {
    System.out.println(anime.title());        // Sousou no Frieren
    System.out.println(anime.year());         // 2023
    System.out.println(anime.averageScore()); // e.g. 90.0 (always 0–100)
    System.out.println(anime.genres());       // [Adventure, Drama, Fantasy]
});
```

## The unified model

The result is always the same `Anime`, no matter which source answered:

```java
public record Anime(
        String id,            // e.g. "anilist:1"
        String title,
        Integer year,
        String description,
        String imageUrl,
        Double averageScore,  // normalised to 0–100 across all sources
        List<String> genres,  // never null (empty when unknown)
        Integer episodes,
        String status,        // airing status as reported by the source
        String source         // "AniList", "TMDb" or "Jikan"
) {}
```

A field a source does not provide is `null` (or an empty list for `genres`).

## How it works

`searchAnime` orchestrates the three sources so you don't have to:

1. **Cache** — if enabled and the title was looked up recently, return it immediately.
2. **AniList** is queried first as the primary source.
3. **TMDb** fills in any missing fields (e.g. a poster) when a token is configured — *best-effort*: a TMDb failure never fails your search.
4. **Jikan** is the fallback when AniList has no match.

Scores from every source are normalised to a **0–100** scale, so `averageScore`
always means the same thing.

## Configuration

Everything is set through the `TsunagiConfig` builder. All options are optional
and have sensible defaults:

```java
TsunagiConfig config = TsunagiConfig.builder()
        .tmdbToken(System.getenv("TMDB_TOKEN"))    // default: none (TMDb disabled)
        .cacheEnabled(true)                        // default: false
        .cacheTtl(Duration.ofMinutes(10))          // default: 10 minutes
        .cacheMaxSize(1000)                        // default: 1000 (LRU eviction)
        .retryEnabled(true)                        // default: true
        .retryMaxAttempts(3)                       // default: 3
        .retryInitialDelay(Duration.ofMillis(500)) // default: 500 ms
        .requestTimeout(Duration.ofSeconds(30))    // default: 30 seconds
        .userAgent("MyApp/1.0 (+https://example.com)") // default: none — see note
        .build();
```

| Option | Required | Default |
|--------|----------|---------|
| `tmdbToken` | optional | none (TMDb enrichment skipped) |
| `cacheEnabled` / `cacheTtl` / `cacheMaxSize` | optional | `false` / 10 min / 1000 |
| `retryEnabled` / `retryMaxAttempts` / `retryInitialDelay` | optional | `true` / 3 / 500 ms |
| `requestTimeout` | optional | 30 s |
| `userAgent` | optional | none |

> **Note on `userAgent`:** the JDK treats `User-Agent` as a restricted header, so it
> is only sent when you set it *and* start the JVM with
> `-Djdk.httpclient.allowRestrictedHeaders=user-agent`. Some sources behind a CDN
> (e.g. AniList via Cloudflare) require an identifiable User-Agent.

### TMDb token

The TMDb token is a **v4 read access token**, taken from your environment — never
hardcode it. Without it, Tsunagi simply skips the TMDb enrichment step and relies
on AniList and Jikan.

## Error handling

All failures are unchecked and rooted at `TsunagiException`, so you only catch
what you care about:

```java
try {
    Optional<Anime> anime = tsunagi.searchAnime("Frieren");
} catch (RateLimitException e) {
    // a source returned HTTP 429
} catch (ApiException e) {
    System.err.println(e.source() + " failed with " + e.statusCode());
} catch (TsunagiException e) {
    // any other Tsunagi failure (network, timeout, parsing, ...)
}
```

| Exception | Meaning |
|-----------|---------|
| `TsunagiException` | base type for every Tsunagi error |
| `ApiException` | a source returned a non-2xx status (`source()`, `statusCode()`) |
| `RateLimitException` | a source returned HTTP 429 |
| `SourceUnavailableException` | network failure, timeout or interruption |

Transient failures (network, 5xx, 429) are retried with exponential backoff;
client errors (4xx) fail fast.

## Using the sources directly

The individual clients are public too, if you only want one:

```java
AniListClient anilist = new AniListClient();
Optional<Anime> anime = anilist.searchAnime("Naruto");

JikanClient jikan = new JikanClient(); // owns a 3 req/s rate limiter
TmdbClient tmdb = new TmdbClient(System.getenv("TMDB_TOKEN"));
```

### Rich source models (since 1.1.0)

Beyond the unified `Anime`, the AniList and TMDb clients expose each source's
richer data directly, as source-shaped records — so you get every field the API
provides:

```java
// AniList — most popular anime, paginated internally, with full metadata
List<AniListMedia> popular = anilist.fetchPopular(50);
for (AniListMedia m : popular) {
    m.title().romaji();
    m.studios().nodes();    // studios, incl. isAnimationStudio
    m.characters().edges(); // up to 6 main characters (role, name, image)
    m.tags();               // tags with rank
}

// TMDb — search, details, watch providers and trailers; you pass the language
TmdbSearchResponse tv = tmdb.searchTv("Attack on Titan", "es-ES");
long id = tv.results().get(0).id();
tmdb.getTvDetails(id, "es-ES");   // localized overview
tmdb.getWatchProviders(id);       // providers by country (flatrate/free/rent/buy)
tmdb.getTrailers(id, "es-ES");    // videos (YouTube keys, ...)
```

Both clients also accept optional `userAgent`, `RetryPolicy` and
`TokenBucketRateLimiter` parameters through their canonical constructors.

## Limitations

- **TMDb** is movie/TV oriented; in Tsunagi it is used mainly for posters and
  scores. Its search endpoint does not return genres, episode counts or status,
  so those come from AniList/Jikan.
- Tsunagi searches and returns the **single best match** per query; it is not a
  full catalogue/browse API.
- All calls are **synchronous** (blocking) on the calling thread.

## Building and testing

```bash
mvn test          # run the test suite (no network required)
mvn install       # install into your local ~/.m2 repository
```

The test suite never calls the real APIs: responses are mapped from canned JSON
and time-based components use injectable clocks, so it is fast and deterministic.
A few `@Disabled` manual tests can be run by hand against the live APIs.

A runnable end-to-end example lives in [`examples/quickstart`](examples/quickstart).

## Releasing

Publishing to Maven Central is documented in [RELEASING.md](RELEASING.md).

## Roadmap

- [x] Unified `Anime` model
- [x] AniList, TMDb and Jikan clients
- [x] Token-bucket rate limiting
- [x] Retries with exponential backoff
- [x] Bounded in-memory caching
- [x] HTTP timeouts and input validation
- [x] Unified `TsunagiClient` facade
- [x] Maven Central publishing setup
- [x] First release on Maven Central
- [x] Rich source models + paginated popular fetch (1.1.0)
- [ ] Optional VS Code extension ([tsunagi-vscode](https://github.com/diegoalegil/tsunagi-vscode))

## License

[MIT](LICENSE) © Diego Gil
