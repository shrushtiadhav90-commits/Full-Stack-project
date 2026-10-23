package com.nexus.quiz.dto.faculty;

import java.util.List;

public record QuizQuestionResponse(
        Long quizQuestionId,
        Long questionId,
        int sortOrder,
        int marks,
        String classLevel,
        String topic,
        String type,
        String text,
        String imageUrl,
        List<QuestionOptionResponse> options) {
}
