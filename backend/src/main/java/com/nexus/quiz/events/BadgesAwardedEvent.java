package com.nexus.quiz.events;

import java.util.List;

public record BadgesAwardedEvent(Long studentId, List<Long> badgeIds, Long quizId) {

    public BadgesAwardedEvent {
        badgeIds = List.copyOf(badgeIds);
    }
}
