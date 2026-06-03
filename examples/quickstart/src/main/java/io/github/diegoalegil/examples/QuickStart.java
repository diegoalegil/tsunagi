package io.github.diegoalegil.examples;

import io.github.diegoalegil.tsunagi.TsunagiClient;
import io.github.diegoalegil.tsunagi.TsunagiConfig;
import io.github.diegoalegil.tsunagi.model.Anime;

import java.util.Optional;

/**
 * Minimal end-to-end example: configure Tsunagi, search for an anime and print
 * the unified result. Pass a title as an argument, or default to "Cowboy Bebop".
 */
public final class QuickStart {

    public static void main(String[] args) {
        String query = args.length > 0 ? String.join(" ", args) : "Cowboy Bebop";

        TsunagiConfig config = TsunagiConfig.builder()
                .tmdbToken(System.getenv("TMDB_TOKEN")) // optional; null is fine
                .cacheEnabled(true)
                .build();

        TsunagiClient tsunagi = new TsunagiClient(config);

        Optional<Anime> result = tsunagi.searchAnime(query);

        if (result.isEmpty()) {
            System.out.println("No anime found for: " + query);
            return;
        }

        Anime anime = result.get();
        System.out.println("id:    " + anime.id());
        System.out.println("title: " + anime.title());
        System.out.println("year:  " + anime.year());
        System.out.println("score: " + anime.averageScore());
        System.out.println("image: " + anime.imageUrl());
    }
}
