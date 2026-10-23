package com.nexus.quiz.dto.faculty;

public record QuestionOptionResponse(
        Long id,
        String optionKey,
        String optionText,
        boolean isCorrect,
        int sortOrder) {
}
