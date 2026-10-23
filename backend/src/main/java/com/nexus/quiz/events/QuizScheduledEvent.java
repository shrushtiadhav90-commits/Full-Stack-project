package com.nexus.quiz.events;

import java.util.List;

public record QuizScheduledEvent(Long quizId, List<Long> classIds) {

    public QuizScheduledEvent {
        classIds = List.copyOf(classIds);
    }
}
