package com.nexus.quiz.dto.faculty;

import com.nexus.quiz.entity.Quiz;
import com.nexus.quiz.entity.ShowAnswers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record QuizResponse(
        Long id,
        String title,
        String description,
        Long subjectId,
        String subjectName,
        Long createdBy,
        Integer durationMinutes,
        String startTime,
        String endTime,
        int maxAttempts,
        boolean shuffleQuestions,
        boolean shuffleOptions,
        ShowAnswers showAnswers,
        BigDecimal passPercentage,
        int totalMarks,
        Quiz.EffectiveStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<QuizClassResponse> classes,
        List<QuizQuestionResponse> questions) {
}
