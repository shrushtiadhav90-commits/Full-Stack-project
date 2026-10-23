package com.nexus.quiz.service;

import com.nexus.quiz.entity.QuizStatus;
import com.nexus.quiz.events.QuizClosedEvent;
import com.nexus.quiz.repository.QuizRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuizStatusSchedulerTest {

    @Test
    void closesOnlyConditionallyExpiredScheduledQuizzesAndIsIdempotent() {
        QuizRepository quizRepository = mock(QuizRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        when(quizRepository.findExpiredQuizIds(eq(QuizStatus.SCHEDULED), any()))
                .thenReturn(List.of(12L, 13L));
        when(quizRepository.closeIfExpired(eq(12L), any())).thenReturn(1, 0);
        when(quizRepository.closeIfExpired(eq(13L), any())).thenReturn(0, 0);
        QuizStatusScheduler scheduler =
                new QuizStatusScheduler(quizRepository, eventPublisher, transactionManager);

        scheduler.closeExpiredQuizzes();
        scheduler.closeExpiredQuizzes();

        verify(quizRepository, times(2)).findExpiredQuizIds(eq(QuizStatus.SCHEDULED), any());
        verify(quizRepository, times(2)).closeIfExpired(eq(12L), any());
        verify(quizRepository, times(2)).closeIfExpired(eq(13L), any());
        verify(eventPublisher, times(1)).publishEvent(new QuizClosedEvent(12L));
        verify(eventPublisher, never()).publishEvent(new QuizClosedEvent(13L));
        verify(transactionManager, times(2)).commit(any());
    }
}
