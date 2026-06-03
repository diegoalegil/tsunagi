package io.github.diegoalegil.tsunagi.anilist;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A character's name. {@code native} is a Java keyword, so the native-language
 * name is bound to {@code nativeName} via {@link JsonProperty}.
 */
public record AniListCharacterName(String full, @JsonProperty("native") String nativeName) {
}
