package com.nexus.quiz.dto.faculty;

import java.math.BigDecimal;
import java.util.List;

public record QuizReportResponse(
        Long quizId,
        String quizTitle,
        long attemptedCount,
        long notAttemptedCount,
        List<QuizReportStudentResponse> studentsNotAttempted,
        BigDecimal classAverage,
        BigDecimal highestPercentage,
        BigDecimal lowestPercentage,
        BigDecimal passPercentage,
        List<QuizScoreBucketResponse> scoreDistribution,
        List<QuizReportStudentResponse> students) {

    public QuizReportResponse {
        studentsNotAttempted = List.copyOf(studentsNotAttempted);
        scoreDistribution = List.copyOf(scoreDistribution);
        students = List.copyOf(students);
    }
}
