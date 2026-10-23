package com.nexus.quiz.dto.faculty;

import com.nexus.quiz.entity.Quiz;

import java.util.List;
import java.util.Map;

public record FacultyDashboardResponse(
        List<FacultyAssignmentOptionResponse> assignments,
        List<QuizResponse> upcomingQuizzes,
        List<QuizResponse> liveQuizzes,
        long pendingShortAnswerCount,
        Map<Quiz.EffectiveStatus, Long> quizCounts) {
}
