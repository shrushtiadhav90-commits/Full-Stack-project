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
        BasicAttemptFinalizer finalizer = new BasicAttemptFinalizer(repository);
        Instant started = Instant.now().minusSeconds(15);
        QuizAttempt attempt = QuizAttempt.builder().startedAt(started).build();

        finalizer.submit(attempt);

        assertEquals(AttemptStatus.SUBMITTED, attempt.getStatus());
        assertTrue(attempt.getIsAutoSubmitted());
        assertNotNull(attempt.getSubmittedAt());
        assertTrue(attempt.getTimeTakenSeconds() >= 15);
        verify(repository).save(attempt);
    }
}
