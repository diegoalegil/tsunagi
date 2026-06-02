package io.github.diegoalegil.tsunagi.model;

public record Anime(
    String id,
    String title,
    Integer year,
    String description,
    String imageUrl,
    Double averageScore
) {
}