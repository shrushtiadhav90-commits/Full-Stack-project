package com.nexus.quiz.dto.admin;

import jakarta.validation.constraints.NotNull;

public record FacultyAssignmentRequest(
        @NotNull Long facultyUserId,
        @NotNull Long classId,
        @NotNull Long subjectId) {
}
