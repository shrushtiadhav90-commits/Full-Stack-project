package com.nexus.quiz.service;

import com.nexus.quiz.entity.QuizStatus;
import com.nexus.quiz.events.QuizClosedEvent;
import com.nexus.quiz.repository.QuizRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Component
public class QuizStatusScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuizStatusScheduler.class);

    private final QuizRepository quizRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public QuizStatusScheduler(
            QuizRepository quizRepository,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager) {
        this.quizRepository = quizRepository;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelay = 60_000)
    public void closeExpiredQuizzes() {
        Instant now = Instant.now();
        try {
            transactionTemplate.executeWithoutResult(status -> closeExpiredQuizzes(now));
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to close expired quizzes; the scheduler will retry", exception);
        }
    }

    private void closeExpiredQuizzes(Instant now) {
        for (Long quizId : quizRepository.findExpiredQuizIds(QuizStatus.SCHEDULED, now)) {
            if (quizRepository.closeIfExpired(quizId, now) == 1) {
                eventPublisher.publishEvent(new QuizClosedEvent(quizId));
            }
        }
    }
}
