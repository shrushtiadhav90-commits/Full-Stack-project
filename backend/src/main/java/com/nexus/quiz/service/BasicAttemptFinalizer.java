package com.nexus.quiz.service;

import com.nexus.quiz.entity.AttemptStatus;
import com.nexus.quiz.entity.QuizAttempt;
import com.nexus.quiz.repository.QuizAttemptRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class BasicAttemptFinalizer implements AttemptFinalizer {

    private final QuizAttemptRepository attemptRepository;

    public BasicAttemptFinalizer(QuizAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    @Override
    public void submit(QuizAttempt attempt) {
        Instant submittedAt = Instant.now();
        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(submittedAt);
        attempt.setIsAutoSubmitted(true);
        long timeTaken = Duration.between(attempt.getStartedAt(), submittedAt).getSeconds();
        attempt.setTimeTakenSeconds((int) Math.min(Integer.MAX_VALUE, Math.max(0, timeTaken)));
        attemptRepository.save(attempt);
    }
}
