package com.nexus.quiz.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSettingsRequest(
        @NotBlank @Size(min = 1, max = 150) String schoolName,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a #RRGGBB hex value")
        String primaryColor,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a #RRGGBB hex value")
        String secondaryColor,
        @NotNull Boolean leaderboardEnabled,
        @NotNull Boolean emailNotificationsEnabled) {
}
