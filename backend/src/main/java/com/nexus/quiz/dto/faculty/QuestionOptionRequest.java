package com.nexus.quiz.dto.faculty;

import jakarta.validation.constraints.NotBlank;

public record QuestionOptionRequest(
        String optionKey,
        @NotBlank String optionText,
        boolean isCorrect,
        Integer sortOrder) {
}
