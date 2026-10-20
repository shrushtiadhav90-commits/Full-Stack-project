package com.nexus.quiz.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AcademicYearRequest(
        @NotBlank @Size(max = 20) String name,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        boolean current) {
}
