package com.nexus.quiz.events;

public record AttemptGradedEvent(Long attemptId, Long studentId, Long quizId) {
}
