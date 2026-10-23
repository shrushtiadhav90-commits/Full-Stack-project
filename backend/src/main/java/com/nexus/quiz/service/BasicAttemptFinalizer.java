package com.nexus.quiz.service;

import com.nexus.quiz.entity.AttemptStatus;
import com.nexus.quiz.entity.QuizAttempt;
import com.nexus.quiz.repository.QuizAttemptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class BasicAttemptFinalizer implements AttemptFinalizer {

    private final QuizAttemptRepository attemptRepository;
    private final GradingService gradingService;

    public BasicAttemptFinalizer(
            QuizAttemptRepository attemptRepository, GradingService gradingService) {
        this.attemptRepository = attemptRepository;
        this.gradingService = gradingService;
    }

    @Override
    @Transactional
    public void submit(QuizAttempt attempt) {
        Instant submittedAt = Instant.now();
        long timeTaken = Duration.between(attempt.getStartedAt(), submittedAt).getSeconds();
        int seconds = (int) Math.min(Integer.MAX_VALUE, Math.max(0, timeTaken));
        if (attemptRepository.submitIfInProgress(attempt.getId(), submittedAt, seconds, true) == 0) {
            return;
        }
        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(submittedAt);
        attempt.setIsAutoSubmitted(true);
        attempt.setTimeTakenSeconds(seconds);
        gradingService.autoGradeAndFinalize(attempt);
    }
}
