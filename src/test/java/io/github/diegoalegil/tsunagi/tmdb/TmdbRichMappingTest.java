package io.github.diegoalegil.tsunagi.tmdb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        // flatrate provider mapped from snake_case keys.
        TmdbProvider cr = es.flatrate().get(0);
        assertEquals(283, cr.providerId());
        assertEquals("Crunchyroll", cr.providerName());
        assertEquals("/cr.jpg", cr.logoPath());
        assertEquals(1, cr.displayPriority());
        // All four monetization buckets are present and mapped.
        assertEquals("FreeTV", es.free().get(0).providerName());
        assertEquals("RentTV", es.rent().get(0).providerName());
        assertEquals("BuyTV", es.buy().get(0).providerName());

        TmdbCountryProviders mx = response.results().get("MX");
        assertEquals("Netflix", mx.flatrate().get(0).providerName());
        // Buckets absent in the JSON stay null.
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
        // The body carries far more than "overview"; unknown fields must be
        // ignored (FAIL_ON_UNKNOWN_PROPERTIES = false), not throw.
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
