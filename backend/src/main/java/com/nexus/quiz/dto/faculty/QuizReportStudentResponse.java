package com.nexus.quiz.dto.faculty;

import java.math.BigDecimal;

public record QuizReportStudentResponse(
        Long studentId,
        String studentName,
        BigDecimal bestAttemptPercentage,
        int attemptsUsed,
        String status) {
}
