package com.nexus.quiz.dto.faculty;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.nexus.quiz.entity.ShowAnswers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record QuizRequest(
        @NotBlank @Size(max = 200) String title,
        @JsonAlias("instructions") String description,
        @NotNull Long subjectId,
        @NotEmpty List<@NotNull Long> classIds,
        @Valid List<QuizQuestionRequest> questions,
        @NotNull @Positive Integer durationMinutes,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        @NotNull @Positive Integer maxAttempts,
        @NotNull Boolean shuffleQuestions,
        @NotNull Boolean shuffleOptions,
        @NotNull ShowAnswers showAnswers,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal passPercentage) {

    public QuizRequest {
        maxAttempts = maxAttempts == null ? 1 : maxAttempts;
        shuffleQuestions = shuffleQuestions == null ? false : shuffleQuestions;
        shuffleOptions = shuffleOptions == null ? false : shuffleOptions;
        showAnswers = showAnswers == null ? ShowAnswers.AFTER_END : showAnswers;
        passPercentage = passPercentage == null ? new BigDecimal("40.00") : passPercentage;
    }
}
