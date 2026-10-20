package com.nexus.quiz.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SchoolClassRequest(
        @NotBlank
        @Pattern(regexp = "Class (?:[1-9]|1[0-2])", message = "Name must be Class 1 through Class 12")
        String name,
        @NotBlank @Size(max = 10) String section,
        @NotNull Long academicYearId) {
}
