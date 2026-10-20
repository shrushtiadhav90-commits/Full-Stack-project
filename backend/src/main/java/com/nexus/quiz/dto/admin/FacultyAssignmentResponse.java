package com.nexus.quiz.dto.admin;

public record FacultyAssignmentResponse(
        Long id,
        Long facultyUserId,
        String facultyName,
        Long classId,
        String className,
        String section,
        Long subjectId,
        String subjectName) {
}
