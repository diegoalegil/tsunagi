package io.github.diegoalegil.tsunagi.tmdb;

import io.github.diegoalegil.tsunagi.model.Anime;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class TmdbClientManualTest {

    @Test
    @Disabled("Manual test: calls the real TMDb API; needs TMDB_TOKEN in the environment")
    void searchesShowInRealTmdbApi() throws Exception {
        String token = System.getenv("TMDB_TOKEN");
        TmdbClient client = new TmdbClient(token);

        Optional<Anime> anime = client.searchAnime("Cowboy Bebop");

        System.out.println(anime);
    }

    @Test
    @Disabled("Manual test: calls the real TMDb API; needs TMDB_TOKEN in the environment")
    void exercisesRichEndpointsInRealTmdbApi() throws Exception {
        String token = System.getenv("TMDB_TOKEN");
        TmdbClient client = new TmdbClient(token);

        TmdbSearchResponse search = client.searchTv("Attack on Titan", "es-ES");
        System.out.println(search);

        Long id = search.results().get(0).id();
        System.out.println(client.getTvDetails(id, "es-ES"));
        System.out.println(client.getWatchProviders(id));
        System.out.println(client.getTrailers(id, "es-ES"));
    }
}
