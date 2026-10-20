package com.nexus.quiz.dto.admin;

public record PublicSettingsResponse(
        String schoolName,
        String logoUrl,
        String primaryColor,
        String secondaryColor) {
}
