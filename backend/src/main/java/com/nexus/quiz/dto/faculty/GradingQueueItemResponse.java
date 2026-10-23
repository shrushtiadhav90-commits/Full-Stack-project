package com.nexus.quiz.dto.faculty;

public record GradingQueueItemResponse(
        Long answerId,
        Long quizId,
        String quizTitle,
        String studentName,
        String className,
        String section,
        String questionText,
        String modelAnswer,
        String textAnswer,
        Integer maximumMarks,
        Long attemptId) {
}
