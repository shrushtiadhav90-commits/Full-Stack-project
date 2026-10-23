package com.nexus.quiz.dto.faculty;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record GradeAnswerRequest(
        @NotNull @DecimalMin("0.00") BigDecimal marksAwarded,
        @Size(max = 5000) String feedback) {
}
