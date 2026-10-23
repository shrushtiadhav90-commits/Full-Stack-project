package com.nexus.quiz.dto.faculty;

public record FacultyAssignmentOptionResponse(
        Long classId,
        String className,
        String section,
        Long subjectId,
        String subjectName) {
}
