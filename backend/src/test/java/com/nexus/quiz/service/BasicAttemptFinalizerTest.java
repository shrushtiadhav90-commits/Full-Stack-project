package com.nexus.quiz.service;

import com.nexus.quiz.entity.AttemptStatus;
import com.nexus.quiz.entity.QuizAttempt;
import com.nexus.quiz.repository.QuizAttemptRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BasicAttemptFinalizerTest {

    @Test
    void submitsAttemptAndMarksItAsAutomaticallySubmitted() {
        QuizAttemptRepository repository = mock(QuizAttemptRepository.class);
        GradingService gradingService = mock(GradingService.class);
        BasicAttemptFinalizer finalizer = new BasicAttemptFinalizer(repository, gradingService);
        Instant started = Instant.now().minusSeconds(15);
        QuizAttempt attempt = QuizAttempt.builder().id(1L).startedAt(started)
                .status(AttemptStatus.IN_PROGRESS).build();
        when(repository.submitIfInProgress(eq(1L), any(), anyInt(), eq(true))).thenReturn(1);

        finalizer.submit(attempt);

        assertEquals(AttemptStatus.SUBMITTED, attempt.getStatus());
        assertTrue(attempt.getIsAutoSubmitted());
        assertNotNull(attempt.getSubmittedAt());
        assertTrue(attempt.getTimeTakenSeconds() >= 15);
        verify(repository).submitIfInProgress(eq(1L), any(), anyInt(), eq(true));
        verify(gradingService).autoGradeAndFinalize(attempt);
    }

    @Test
    void doesNotSubmitOrGradeAnAttemptAlreadyClaimedByAnotherSubmitter() {
        QuizAttemptRepository repository = mock(QuizAttemptRepository.class);
        GradingService gradingService = mock(GradingService.class);
        BasicAttemptFinalizer finalizer = new BasicAttemptFinalizer(repository, gradingService);
        QuizAttempt attempt = QuizAttempt.builder().id(2L).startedAt(Instant.now())
                .status(AttemptStatus.IN_PROGRESS).build();
        when(repository.submitIfInProgress(eq(2L), any(), anyInt(), eq(true))).thenReturn(0);

        finalizer.submit(attempt);

        assertEquals(AttemptStatus.IN_PROGRESS, attempt.getStatus());
        verify(gradingService, never()).autoGradeAndFinalize(any());
    }
}
