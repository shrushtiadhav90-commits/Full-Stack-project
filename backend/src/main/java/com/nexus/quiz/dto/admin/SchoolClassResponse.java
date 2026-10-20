package com.nexus.quiz.dto.admin;

public record SchoolClassResponse(
        Long id,
        String name,
        String section,
        Long academicYearId) {
}
