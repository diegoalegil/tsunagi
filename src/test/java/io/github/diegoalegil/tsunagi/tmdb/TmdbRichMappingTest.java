package io.github.diegoalegil.tsunagi.tmdb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the rich TMDb endpoint mappings (readValue into records) with canned
 * responses, without calling the real API.
 */
class TmdbRichMappingTest {

    private final TmdbClient client = new TmdbClient("test-token");

    @Test
    void parsesSearchWithSnakeCaseFields() throws Exception {
        String json = """
                {
                  "page": 1,
                  "results": [
                    {
                      "id": 1429,
                      "name": "Attack on Titan",
                      "original_name": "進撃の巨人",
                      "overview": "Humans versus titans.",
                      "first_air_date": "2013-04-07",
                      "origin_country": ["JP"],
                      "poster_path": "/aot.jpg",
                      "popularity": 350.5
                    }
                  ],
                  "total_results": 1,
                  "total_pages": 1
                }
                """;

        TmdbSearchResponse response = client.parseSearch(json);

        assertEquals(1, response.page());
        assertEquals(1, response.totalResults());
        assertEquals(1, response.totalPages());
        assertEquals(1, response.results().size());
        TmdbSearchResult r = response.results().get(0);
        assertEquals(1429L, r.id());
        assertEquals("Attack on Titan", r.name());
        assertEquals("進撃の巨人", r.originalName());
        assertEquals("Humans versus titans.", r.overview());
        assertEquals("2013-04-07", r.firstAirDate());
        assertEquals(List.of("JP"), r.originCountry());
        assertEquals("/aot.jpg", r.posterPath());
        assertEquals(350.5, r.popularity());
        // A /search/tv result has no media_type / movie fields → treated as TV.
        assertNull(r.mediaType());
        assertTrue(r.isTv());
        assertFalse(r.isMovie());
        assertEquals("Attack on Titan", r.displayName());
        assertEquals("進撃の巨人", r.displayOriginalName());
        assertEquals("2013-04-07", r.displayDate());
    }

    @Test
    void parsesMultiResultForMovieUsingDisplayHelpers() throws Exception {
        // /search/multi mixes tv, movie and person; a movie carries
        // title/original_title/release_date and media_type = "movie".
        String json = """
                {
                  "page": 1,
                  "results": [
                    {
                      "id": 568332,
                      "media_type": "movie",
                      "title": "Suzume",
                      "original_title": "すずめの戸締まり",
                      "overview": "A girl closes doors that release disaster.",
                      "release_date": "2022-11-11",
                      "origin_country": ["JP"],
                      "poster_path": "/suzume.jpg",
                      "popularity": 120.0,
                      "original_language": "ja"
                    }
                  ],
                  "total_results": 1,
                  "total_pages": 1
                }
                """;

        TmdbSearchResult r = client.parseSearch(json).results().get(0);

        assertEquals(568332L, r.id());
        assertEquals("movie", r.mediaType());
        assertTrue(r.isMovie());
        assertFalse(r.isTv());
        assertEquals("ja", r.originalLanguage());
        assertEquals("2022-11-11", r.releaseDate());
        // name/first_air_date are null for a movie → helpers fall back to title/release_date.
        assertNull(r.name());
        assertEquals("Suzume", r.title());
        assertEquals("Suzume", r.displayName());
        assertEquals("すずめの戸締まり", r.displayOriginalName());
        assertEquals("2022-11-11", r.displayDate());
    }

    @Test
    void parsesMultiResultMixingTvMovieAndPerson() throws Exception {
        // /search/multi can return tv, movie and person entries in one payload.
        // isTv()/isMovie() must classify each so callers can filter cleanly.
        String json = """
                {
                  "page": 1,
                  "results": [
                    { "id": 1, "media_type": "tv", "name": "Naruto", "first_air_date": "2002-10-03" },
                    { "id": 2, "media_type": "movie", "title": "Your Name", "release_date": "2016-08-26" },
                    { "id": 3, "media_type": "person", "name": "Hayao Miyazaki" }
                  ],
                  "total_results": 3,
                  "total_pages": 1
                }
                """;

        List<TmdbSearchResult> results = client.parseSearch(json).results();

        assertEquals(3, results.size());

        TmdbSearchResult tv = results.get(0);
        assertTrue(tv.isTv());
        assertFalse(tv.isMovie());
        assertEquals("Naruto", tv.displayName());
        assertEquals("2002-10-03", tv.displayDate());

        TmdbSearchResult movie = results.get(1);
        assertTrue(movie.isMovie());
        assertFalse(movie.isTv());
        assertEquals("Your Name", movie.displayName());
        assertEquals("2016-08-26", movie.displayDate());

        // A person is neither tv nor movie; callers filter it out via the helpers.
        TmdbSearchResult person = results.get(2);
        assertFalse(person.isTv());
        assertFalse(person.isMovie());
    }

    @Test
    void parsesProvidersForMultipleCountriesAndAllTypes() throws Exception {
        String json = """
                {
                  "id": 1429,
                  "results": {
                    "ES": {
                      "link": "https://example.com/es",
                      "flatrate": [ { "provider_id": 283, "provider_name": "Crunchyroll", "logo_path": "/cr.jpg", "display_priority": 1 } ],
                      "free": [ { "provider_id": 100, "provider_name": "FreeTV", "logo_path": "/free.jpg", "display_priority": 2 } ],
                      "rent": [ { "provider_id": 200, "provider_name": "RentTV", "logo_path": "/rent.jpg", "display_priority": 3 } ],
                      "buy": [ { "provider_id": 300, "provider_name": "BuyTV", "logo_path": "/buy.jpg", "display_priority": 4 } ]
                    },
                    "MX": {
                      "link": "https://example.com/mx",
                      "flatrate": [ { "provider_id": 8, "provider_name": "Netflix", "logo_path": "/nf.jpg", "display_priority": 1 } ]
                    }
                  }
                }
                """;

        TmdbProvidersResponse response = client.parseProviders(json);

        assertEquals(1429L, response.id());
        assertTrue(response.results().containsKey("ES"));
        assertTrue(response.results().containsKey("MX"));

        TmdbCountryProviders es = response.results().get("ES");
        assertEquals("https://example.com/es", es.link());
        TmdbProvider cr = es.flatrate().get(0);
        assertEquals(283, cr.providerId());
        assertEquals("Crunchyroll", cr.providerName());
        assertEquals("/cr.jpg", cr.logoPath());
        assertEquals(1, cr.displayPriority());
        assertEquals("FreeTV", es.free().get(0).providerName());
        assertEquals("RentTV", es.rent().get(0).providerName());
        assertEquals("BuyTV", es.buy().get(0).providerName());

        TmdbCountryProviders mx = response.results().get("MX");
        assertEquals("Netflix", mx.flatrate().get(0).providerName());
        assertNull(mx.free());
        assertNull(mx.rent());
        assertNull(mx.buy());
    }

    @Test
    void parsesVideosKeepingSiteAndType() throws Exception {
        String json = """
                {
                  "id": 1429,
                  "results": [
                    { "key": "abc123", "site": "YouTube", "type": "Trailer", "name": "Official Trailer" },
                    { "key": "xyz789", "site": "Vimeo", "type": "Clip", "name": "A clip" }
                  ]
                }
                """;

        TmdbVideosResponse response = client.parseVideos(json);

        assertEquals(1429L, response.id());
        assertEquals(2, response.results().size());
        TmdbVideo trailer = response.results().get(0);
        assertEquals("abc123", trailer.key());
        assertEquals("YouTube", trailer.site());
        assertEquals("Trailer", trailer.type());
        assertEquals("Official Trailer", trailer.name());
        assertEquals("Vimeo", response.results().get(1).site());
    }

    @Test
    void parsesTvDetailsIgnoringUnknownFields() throws Exception {
        String json = """
                {
                  "id": 1429,
                  "name": "Attack on Titan",
                  "overview": "Sinopsis en español."
                }
                """;

        TmdbTvDetailsResponse response = client.parseTvDetails(json);

        assertEquals("Sinopsis en español.", response.overview());
    }
}
