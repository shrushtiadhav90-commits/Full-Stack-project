package com.nexus.quiz.dto.admin;

public record CreatedUserResponse(UserSummaryResponse user, String temporaryPassword) {
}
