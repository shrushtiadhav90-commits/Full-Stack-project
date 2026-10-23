package com.nexus.quiz.dto.faculty;

import com.nexus.quiz.entity.QuestionDifficulty;
import com.nexus.quiz.entity.QuestionType;

import java.time.Instant;
import java.util.List;

public record QuestionResponse(
        Long id,
        Long createdBy,
        Long subjectId,
        String subjectName,
        String classLevel,
        String topic,
        QuestionType type,
        String text,
        String imageUrl,
        QuestionDifficulty difficulty,
        int marks,
        String explanation,
        String modelAnswer,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        List<QuestionOptionResponse> options) {
}
