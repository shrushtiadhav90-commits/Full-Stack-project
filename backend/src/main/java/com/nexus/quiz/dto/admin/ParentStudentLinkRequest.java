package com.nexus.quiz.dto.admin;

import com.nexus.quiz.entity.ParentStudent;
import jakarta.validation.constraints.NotNull;

public record ParentStudentLinkRequest(
        @NotNull Long parentUserId,
        @NotNull Long studentId,
        @NotNull ParentStudent.Relation relation) {
}
