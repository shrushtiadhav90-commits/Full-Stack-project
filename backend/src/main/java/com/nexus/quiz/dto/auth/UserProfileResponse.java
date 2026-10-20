package com.nexus.quiz.dto.auth;

import com.nexus.quiz.entity.Role;

public record UserProfileResponse(
        Long id,
        String fullName,
        String username,
        Role role,
        boolean mustChangePassword) {
}
