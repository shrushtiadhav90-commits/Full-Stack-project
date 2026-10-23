package com.nexus.quiz.dto.faculty;

import java.util.List;

public record QuizDuplicateResponse(
        QuizResponse quiz,
        List<SkippedQuizQuestionResponse> skippedQuestions,
        List<QuizClassResponse> skippedClasses) {

    public QuizDuplicateResponse {
        skippedQuestions = List.copyOf(skippedQuestions);
        skippedClasses = List.copyOf(skippedClasses);
    }
}
