package com.nexus.quiz.dto.faculty;

import java.math.BigDecimal;

public record QuizReportStudentResponse(
        Long studentId,
        String studentName,
        String className,
        String section,
        BigDecimal bestAttemptPercentage,
        int attemptsUsed,
        String status) {
}
