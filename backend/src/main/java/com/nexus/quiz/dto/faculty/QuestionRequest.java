package com.nexus.quiz.dto.faculty;

import com.nexus.quiz.entity.QuestionDifficulty;
import com.nexus.quiz.entity.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record QuestionRequest(
        @NotBlank String text,
        @NotNull QuestionType type,
        @NotNull Long subjectId,
        @NotBlank @Size(max = 20) String classLevel,
        @Size(max = 100) String topic,
        @NotNull QuestionDifficulty difficulty,
        @NotNull @Positive Integer marks,
        String explanation,
        String modelAnswer,
        @Valid List<QuestionOptionRequest> options,
        boolean removeImage) {

    public QuestionRequest {
        marks = marks == null ? 1 : marks;
    }
}
