package com.nexus.quiz.dto.auth;

import com.nexus.quiz.entity.Role;

public record AuthTokenResponse(
        String accessToken,
        Role role,
        boolean mustChangePassword,
        AuthUserResponse user) {
}
