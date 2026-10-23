package com.nexus.quiz.service;

import com.nexus.quiz.dto.admin.PageResponse;
import com.nexus.quiz.dto.faculty.GradeAnswerResponse;
import com.nexus.quiz.dto.faculty.GradingQueueItemResponse;
import com.nexus.quiz.entity.*;
import com.nexus.quiz.events.AttemptGradedEvent;
import com.nexus.quiz.exception.ConflictException;
import com.nexus.quiz.exception.ResourceNotFoundException;
import com.nexus.quiz.exception.ServiceValidationException;
import com.nexus.quiz.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class GradingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GradingService.class);
    private final AttemptAnswerRepository answerRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizQuestionSnapshotRepository snapshotRepository;
    private final FacultyAssignmentRepository assignmentRepository;
    private final QuizRepository quizRepository;
    private final FacultyIdentity facultyIdentity;
    private final ApplicationEventPublisher eventPublisher;

    public GradingService(
            AttemptAnswerRepository answerRepository,
            QuizAttemptRepository attemptRepository,
            QuizQuestionSnapshotRepository snapshotRepository,
            FacultyAssignmentRepository assignmentRepository,
            QuizRepository quizRepository,
            FacultyIdentity facultyIdentity,
            ApplicationEventPublisher eventPublisher) {
        this.answerRepository = answerRepository;
        this.attemptRepository = attemptRepository;
        this.snapshotRepository = snapshotRepository;
        this.assignmentRepository = assignmentRepository;
        this.quizRepository = quizRepository;
        this.facultyIdentity = facultyIdentity;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void autoGrade(QuizAttempt attempt) {
        QuizAttempt managedAttempt = requireLockedAttempt(attempt.getId());
        if (managedAttempt.getStatus() == AttemptStatus.GRADED) {
            return;
        }
        for (AttemptAnswer answer : answerRepository.findByAttemptIdOrderByIdAsc(managedAttempt.getId())) {
            QuizQuestionSnapshot snapshot = requireSnapshot(managedAttempt, answer);
            if (snapshot.getQuestionType() == QuestionType.SHORT_ANSWER) {
                answer.setGradingStatus(GradingStatus.PENDING);
                answer.setIsCorrect(null);
                answer.setMarksAwarded(null);
                continue;
            }
            boolean correct = answer.getSelectedOptionKey() != null
                    && answer.getSelectedOptionKey().equals(snapshot.getCorrectOptionKey());
            answer.setIsCorrect(correct);
            answer.setMarksAwarded(correct
                    ? BigDecimal.valueOf(snapshot.getMarks())
                    : BigDecimal.ZERO);
            answer.setGradingStatus(GradingStatus.AUTO_GRADED);
        }
        answerRepository.flush();
    }

    @Transactional
    public void autoGradeAndFinalize(QuizAttempt attempt) {
        QuizAttempt managedAttempt = requireLockedAttempt(attempt.getId());
        if (managedAttempt.getStatus() == AttemptStatus.GRADED) {
            return;
        }
        autoGradeAnswers(managedAttempt);
        if (!answerRepository.existsPendingShortAnswer(managedAttempt.getId())) {
            finalizeLockedAttempt(managedAttempt);
        }
    }

    @Transactional
    public void finalizeAttempt(QuizAttempt attempt) {
        finalizeAttempt(attempt.getId());
    }

    @Transactional
    public void finalizeAttempt(Long attemptId) {
        QuizAttempt attempt = requireLockedAttempt(attemptId);
        finalizeLockedAttempt(attempt);
    }

    @Transactional(readOnly = true)
    public PageResponse<GradingQueueItemResponse> getGradingQueue(
            int page, int size, Long quizId, Long classId) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100");
        }
        Long facultyId = facultyIdentity.requireFacultyId();
        var queue = answerRepository.findPendingShortAnswers(
                facultyId, quizId, classId, PageRequest.of(page, size, Sort.unsorted()));
        return PageResponse.from(queue);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GradeAnswerResponse gradeAnswer(
            Long answerId, BigDecimal marksAwarded, String feedback) {
        Long facultyId = facultyIdentity.requireFacultyId();
        if (marksAwarded == null || marksAwarded.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceValidationException("marksAwarded", "Marks awarded must be zero or greater");
        }
        AttemptAnswer initialAnswer = answerRepository.findById(answerId)
                .orElseThrow(() -> new ResourceNotFoundException("Answer not found"));
        QuizAttempt attempt = requireLockedAttempt(initialAnswer.getAttempt().getId());
        AttemptAnswer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new ResourceNotFoundException("Answer not found"));
        requireFacultyAssignment(facultyId, attempt, answer);
        if (attempt.getStatus() == AttemptStatus.GRADED) {
            throw new ConflictException("A fully graded attempt cannot be changed");
        }
        if (attempt.getStatus() != AttemptStatus.SUBMITTED) {
            throw new ConflictException("Only submitted attempts can be graded");
        }

        QuizQuestionSnapshot snapshot = requireSnapshot(attempt, answer);
        if (snapshot.getQuestionType() != QuestionType.SHORT_ANSWER) {
            throw new ConflictException("Only short-answer responses can be graded manually");
        }
        BigDecimal maximum = BigDecimal.valueOf(snapshot.getMarks());
        if (marksAwarded.compareTo(maximum) > 0) {
            throw new ServiceValidationException("marksAwarded",
                    "Marks awarded cannot exceed the maximum of " + maximum.stripTrailingZeros().toPlainString());
        }
        answer.setMarksAwarded(marksAwarded);
        answer.setFacultyFeedback(feedback);
        answer.setGradingStatus(GradingStatus.GRADED);
        answerRepository.save(answer);
        if (!answerRepository.existsPendingShortAnswer(attempt.getId())) {
            finalizeLockedAttempt(attempt);
        }
        return new GradeAnswerResponse(
                answer.getId(), answer.getMarksAwarded(), answer.getGradingStatus(),
                attempt.getStatus() == AttemptStatus.GRADED);
    }

    @Transactional(readOnly = true)
    public long countPendingShortAnswers() {
        return answerRepository.countPendingShortAnswers(facultyIdentity.requireFacultyId());
    }

    private void autoGradeAnswers(QuizAttempt attempt) {
        for (AttemptAnswer answer : answerRepository.findByAttemptIdOrderByIdAsc(attempt.getId())) {
            QuizQuestionSnapshot snapshot = requireSnapshot(attempt, answer);
            if (snapshot.getQuestionType() == QuestionType.SHORT_ANSWER) {
                answer.setGradingStatus(GradingStatus.PENDING);
                answer.setIsCorrect(null);
                answer.setMarksAwarded(null);
            } else {
                boolean correct = answer.getSelectedOptionKey() != null
                        && answer.getSelectedOptionKey().equals(snapshot.getCorrectOptionKey());
                answer.setIsCorrect(correct);
                answer.setMarksAwarded(correct
                        ? BigDecimal.valueOf(snapshot.getMarks())
                        : BigDecimal.ZERO);
                answer.setGradingStatus(GradingStatus.AUTO_GRADED);
            }
        }
        answerRepository.flush();
    }

    private void finalizeLockedAttempt(QuizAttempt attempt) {
        if (attempt.getStatus() == AttemptStatus.GRADED) {
            return;
        }
        if (answerRepository.existsPendingShortAnswer(attempt.getId())) {
            return;
        }
        List<AttemptAnswer> answers = answerRepository.findByAttemptIdOrderByIdAsc(attempt.getId());
        BigDecimal total = answers.stream()
                .map(AttemptAnswer::getMarksAwarded)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        attempt.setTotalScore(total);
        int totalMarks = attempt.getQuiz().getTotalMarks() == null
                ? 0 : attempt.getQuiz().getTotalMarks();
        attempt.setPercentage(totalMarks == 0
                ? BigDecimal.ZERO
                : total.multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalMarks), 2, RoundingMode.HALF_UP));
        attempt.setStatus(AttemptStatus.GRADED);
        attemptRepository.save(attempt);
        publishAfterCommit(new AttemptGradedEvent(
                attempt.getId(), attempt.getStudent().getId(), attempt.getQuiz().getId()));
    }

    private QuizAttempt requireLockedAttempt(Long attemptId) {
        return attemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
    }

    private QuizQuestionSnapshot requireSnapshot(QuizAttempt attempt, AttemptAnswer answer) {
        Long questionId = answer.getQuizQuestion().getQuestion().getId();
        return snapshotRepository.findByQuizIdAndQuestionId(attempt.getQuiz().getId(), questionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Question snapshot is missing for attempt " + attempt.getId()));
    }

    private void requireFacultyAssignment(Long facultyId, QuizAttempt attempt, AttemptAnswer answer) {
        Quiz quiz = quizRepository.findById(attempt.getQuiz().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));
        if (!facultyId.equals(quiz.getCreatedBy().getId())) {
            throw new AccessDeniedException("Faculty may only grade their own quizzes");
        }
        Long classId = attempt.getStudent().getSchoolClass().getId();
        if (!assignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(
                facultyId, classId, quiz.getSubject().getId())) {
            throw new AccessDeniedException("Faculty is not assigned to this class and subject");
        }
    }

    private void publishAfterCommit(AttemptGradedEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishSafely(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishSafely(event);
            }
        });
    }

    private void publishSafely(AttemptGradedEvent event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (RuntimeException exception) {
            LOGGER.error("Attempt {} was graded, but its after-commit event failed", event.attemptId(), exception);
        }
    }
}
