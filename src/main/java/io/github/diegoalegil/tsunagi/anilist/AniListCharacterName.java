package io.github.diegoalegil.tsunagi.anilist;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * A character's name. {@code native} is a Java keyword, so the native-language
 * name is bound to {@code nativeName} via {@link JsonProperty}.
 */
public record AniListCharacterName(@Nullable String full, @JsonProperty("native") @Nullable String nativeName) {
}
