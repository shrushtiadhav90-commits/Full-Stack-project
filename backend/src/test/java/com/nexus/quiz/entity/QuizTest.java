package com.nexus.quiz.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuizTest {

    private static final Instant START = Instant.parse("2026-10-23T10:00:00Z");
    private static final Instant END = Instant.parse("2026-10-23T11:00:00Z");

    @Test
    void effectiveStatusDerivesScheduledQuizLifecycleWithoutPersistingLive() {
        Quiz quiz = Quiz.builder()
                .status(QuizStatus.SCHEDULED)
                .startTime(START)
                .endTime(END)
                .build();

        assertEquals(Quiz.EffectiveStatus.SCHEDULED,
                quiz.effectiveStatusAt(START.minusNanos(1)));
        assertEquals(Quiz.EffectiveStatus.LIVE, quiz.effectiveStatusAt(START));
        assertEquals(Quiz.EffectiveStatus.LIVE, quiz.effectiveStatusAt(END.minusNanos(1)));
        assertEquals(Quiz.EffectiveStatus.CLOSED, quiz.effectiveStatusAt(END));
    }

    @Test
    void effectiveStatusPreservesExplicitDraftAndClosedStates() {
        Quiz draft = Quiz.builder().status(QuizStatus.DRAFT).startTime(START).endTime(END).build();
        Quiz closed = Quiz.builder().status(QuizStatus.CLOSED).startTime(START).endTime(END).build();

        assertEquals(Quiz.EffectiveStatus.DRAFT, draft.effectiveStatusAt(START));
        assertEquals(Quiz.EffectiveStatus.CLOSED, closed.effectiveStatusAt(START));
    }
}
