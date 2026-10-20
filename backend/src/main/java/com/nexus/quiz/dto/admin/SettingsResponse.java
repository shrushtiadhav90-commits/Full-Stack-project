package com.nexus.quiz.dto.admin;

public record SettingsResponse(
        String schoolName,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        boolean leaderboardEnabled,
        boolean emailNotificationsEnabled,
        String updatedAt) {
}
