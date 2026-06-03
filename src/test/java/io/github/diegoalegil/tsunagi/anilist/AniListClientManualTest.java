package io.github.diegoalegil.tsunagi.anilist;

import io.github.diegoalegil.tsunagi.model.Anime;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

class AniListClientManualTest {

    @Test
    @Disabled("Manual test: calls the real AniList API")
    void searchesAnimeInRealAniListApi() throws Exception {
        AniListClient client = new AniListClient();

        Optional<Anime> anime = client.searchAnime("Cowboy Bebop");

        System.out.println(anime);
    }

    @Test
    @Disabled("Manual test: calls the real AniList API; to send a User-Agent it needs the "
            + "JVM flag -Djdk.httpclient.allowRestrictedHeaders=user-agent")
    void fetchesPopularFromRealAniListApi() throws Exception {
        AniListClient client = new AniListClient(
                Duration.ofSeconds(30), "Tsunagi-Manual-Test/1.0", null, null);

        List<AniListMedia> media = client.fetchPopular(5);

        media.forEach(m -> System.out.println(
                m.id() + " " + (m.title() == null ? null : m.title().romaji())));
    }
}
