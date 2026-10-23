package com.nexus.quiz.dto.faculty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record QuizQuestionRequest(
        @NotNull Long questionId,
        @NotNull @Positive Integer marks) {
}
