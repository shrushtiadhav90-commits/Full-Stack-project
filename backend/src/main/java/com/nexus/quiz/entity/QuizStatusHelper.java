package com.nexus.quiz.entity;

import java.time.Instant;
import java.util.Objects;

public final class QuizStatusHelper {

    private QuizStatusHelper() {
    }

    public static boolean isOpenForAttempts(Quiz quiz, Instant now) {
        Objects.requireNonNull(quiz, "quiz must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return quiz.getStatus() == QuizStatus.SCHEDULED
                && quiz.getStartTime() != null
                && quiz.getEndTime() != null
                && !now.isBefore(quiz.getStartTime())
                && now.isBefore(quiz.getEndTime());
    }

    public static Quiz.EffectiveStatus derivedStatusAt(Quiz quiz, Instant now) {
        Objects.requireNonNull(quiz, "quiz must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return switch (quiz.getStatus()) {
            case DRAFT -> Quiz.EffectiveStatus.DRAFT;
            case CLOSED -> Quiz.EffectiveStatus.CLOSED;
            case SCHEDULED -> {
                if (quiz.getEndTime() != null && !now.isBefore(quiz.getEndTime())) {
                    yield Quiz.EffectiveStatus.CLOSED;
                }
                if (quiz.getStartTime() != null && now.isBefore(quiz.getStartTime())) {
                    yield Quiz.EffectiveStatus.SCHEDULED;
                }
                yield isOpenForAttempts(quiz, now)
                        ? Quiz.EffectiveStatus.LIVE
                        : Quiz.EffectiveStatus.SCHEDULED;
            }
        };
    }
}
