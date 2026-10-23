package com.nexus.quiz.service;

import com.nexus.quiz.dto.faculty.GradeAnswerResponse;
import com.nexus.quiz.entity.*;
import com.nexus.quiz.events.AttemptGradedEvent;
import com.nexus.quiz.exception.ConflictException;
import com.nexus.quiz.exception.ServiceValidationException;
import com.nexus.quiz.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GradingServiceTest {

    private AttemptAnswerRepository answerRepository;
    private QuizAttemptRepository attemptRepository;
    private QuizQuestionSnapshotRepository snapshotRepository;
    private FacultyAssignmentRepository assignmentRepository;
    private QuizRepository quizRepository;
    private FacultyIdentity facultyIdentity;
    private ApplicationEventPublisher eventPublisher;
    private GradingService service;

    private User faculty;
    private Subject subject;
    private SchoolClass schoolClass;
    private Quiz quiz;
    private Student student;
    private QuizAttempt attempt;

    @BeforeEach
    void setUp() {
        answerRepository = mock(AttemptAnswerRepository.class);
        attemptRepository = mock(QuizAttemptRepository.class);
        snapshotRepository = mock(QuizQuestionSnapshotRepository.class);
        assignmentRepository = mock(FacultyAssignmentRepository.class);
        quizRepository = mock(QuizRepository.class);
        facultyIdentity = mock(FacultyIdentity.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new GradingService(answerRepository, attemptRepository, snapshotRepository,
                assignmentRepository, quizRepository, facultyIdentity, eventPublisher);

        faculty = User.builder().id(1L).role(Role.FACULTY).build();
        subject = Subject.builder().id(10L).name("Math").build();
        schoolClass = SchoolClass.builder().id(20L).name("Class 6").section("A").build();
        quiz = Quiz.builder().id(30L).createdBy(faculty).subject(subject).title("Quiz")
                .totalMarks(10).passPercentage(new BigDecimal("40")).build();
        student = Student.builder().id(40L).user(User.builder().id(41L).fullName("Learner").build())
                .schoolClass(schoolClass).build();
        attempt = QuizAttempt.builder().id(50L).quiz(quiz).student(student)
                .status(AttemptStatus.SUBMITTED).startedAt(Instant.now().minusSeconds(60))
                .build();
        when(attemptRepository.findByIdForUpdate(anyLong())).thenAnswer(invocation ->
                invocation.getArgument(0).equals(50L) ? Optional.of(attempt) : Optional.empty());
        when(facultyIdentity.requireFacultyId()).thenReturn(1L);
        when(quizRepository.findById(30L)).thenReturn(Optional.of(quiz));
    }

    @Test
    void autogradesSelectionsOnlyFromSnapshotsAndFinalizesWhenNoShortAnswersRemain() {
        AttemptAnswer correct = answer(1L, QuestionType.MCQ, "A");
        AttemptAnswer wrong = answer(2L, QuestionType.TRUE_FALSE, "B");
        AttemptAnswer unanswered = answer(3L, QuestionType.MCQ, null);
        when(answerRepository.findByAttemptIdOrderByIdAsc(50L))
                .thenReturn(List.of(correct, wrong, unanswered));
        when(snapshotRepository.findByQuizIdAndQuestionId(30L, 101L))
                .thenReturn(Optional.of(snapshot(QuestionType.MCQ, "A", 4)));
        when(snapshotRepository.findByQuizIdAndQuestionId(30L, 102L))
                .thenReturn(Optional.of(snapshot(QuestionType.TRUE_FALSE, "A", 3)));
        when(snapshotRepository.findByQuizIdAndQuestionId(30L, 103L))
                .thenReturn(Optional.of(snapshot(QuestionType.MCQ, "A", 2)));
        when(answerRepository.existsPendingShortAnswer(50L)).thenReturn(false);

        service.autoGradeAndFinalize(attempt);

        assertEquals(GradingStatus.AUTO_GRADED, correct.getGradingStatus());
        assertTrue(correct.getIsCorrect());
        assertEquals(new BigDecimal("4"), correct.getMarksAwarded());
        assertEquals(GradingStatus.AUTO_GRADED, wrong.getGradingStatus());
        assertFalse(wrong.getIsCorrect());
        assertEquals(BigDecimal.ZERO, wrong.getMarksAwarded());
        assertFalse(unanswered.getIsCorrect());
        assertEquals(BigDecimal.ZERO, unanswered.getMarksAwarded());
        assertEquals(new BigDecimal("40.00"), attempt.getPercentage());
        assertEquals(AttemptStatus.GRADED, attempt.getStatus());
        verify(eventPublisher).publishEvent(new AttemptGradedEvent(50L, 40L, 30L));
    }

    @Test
    void shortAnswerRemainsPendingUntilManuallyGradedAndThenCompletesAttempt() {
        AttemptAnswer answer = answer(4L, QuestionType.SHORT_ANSWER, null);
        answer.setTextAnswer("");
        when(answerRepository.findByAttemptIdOrderByIdAsc(50L)).thenReturn(List.of(answer));
        when(snapshotRepository.findByQuizIdAndQuestionId(30L, 104L))
                .thenReturn(Optional.of(snapshot(QuestionType.SHORT_ANSWER, null, 5)));
        when(answerRepository.existsPendingShortAnswer(50L)).thenReturn(true, false);
        when(answerRepository.findById(4L)).thenReturn(Optional.of(answer));
        when(assignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(1L, 20L, 10L))
                .thenReturn(true);

        service.autoGradeAndFinalize(attempt);

        assertEquals(AttemptStatus.SUBMITTED, attempt.getStatus());
        assertEquals(GradingStatus.PENDING, answer.getGradingStatus());

        GradeAnswerResponse response = service.gradeAnswer(4L, BigDecimal.ZERO, "No response");

        assertEquals(GradingStatus.GRADED, response.gradingStatus());
        assertTrue(response.attemptFullyGraded());
        assertEquals(AttemptStatus.GRADED, attempt.getStatus());
        assertEquals(BigDecimal.ZERO, attempt.getTotalScore());
    }

    @Test
    void finalizeAttemptIsIdempotentAndListenerFailureCannotUndoGrade() {
        AttemptAnswer first = answer(5L, QuestionType.MCQ, null);
        first.setMarksAwarded(new BigDecimal("2.25"));
        AttemptAnswer second = answer(6L, QuestionType.MCQ, null);
        second.setMarksAwarded(new BigDecimal("2.75"));
        when(answerRepository.existsPendingShortAnswer(50L)).thenReturn(false);
        when(answerRepository.findByAttemptIdOrderByIdAsc(50L)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("Listener failure"))
                .when(eventPublisher).publishEvent(any(AttemptGradedEvent.class));

        service.finalizeAttempt(attempt);
        service.finalizeAttempt(attempt);

        assertEquals(new BigDecimal("5.00"), attempt.getTotalScore());
        assertEquals(new BigDecimal("50.00"), attempt.getPercentage());
        assertEquals(AttemptStatus.GRADED, attempt.getStatus());
        verify(attemptRepository, times(1)).save(attempt);
        verify(eventPublisher, times(1)).publishEvent(new AttemptGradedEvent(50L, 40L, 30L));
    }

    @Test
    void gradeAnswerRejectsExcessMarksAndUnassignedFaculty() {
        AttemptAnswer answer = answer(7L, QuestionType.SHORT_ANSWER, null);
        when(answerRepository.findById(7L)).thenReturn(Optional.of(answer));
        when(snapshotRepository.findByQuizIdAndQuestionId(30L, 107L))
                .thenReturn(Optional.of(snapshot(QuestionType.SHORT_ANSWER, null, 3)));
        when(quizRepository.findById(30L)).thenReturn(Optional.of(quiz));
        when(assignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(1L, 20L, 10L))
                .thenReturn(true, false);

        ServiceValidationException tooHigh = assertThrows(ServiceValidationException.class,
                () -> service.gradeAnswer(7L, new BigDecimal("3.01"), null));
        assertTrue(tooHigh.getFieldErrors().get("marksAwarded").contains("maximum of 3"));

        when(assignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(1L, 20L, 10L))
                .thenReturn(false);
        assertThrows(AccessDeniedException.class,
                () -> service.gradeAnswer(7L, BigDecimal.ONE, null));
        verify(answerRepository, never()).save(any());
    }

    @Test
    void queueUsesAssignedFacultyScopeAndPagination() {
        when(answerRepository.findPendingShortAnswers(eq(1L), eq(30L), eq(20L), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        var result = service.getGradingQueue(0, 10, 30L, 20L);

        assertEquals(0, result.totalElements());
        verify(answerRepository).findPendingShortAnswers(eq(1L), eq(30L), eq(20L), any());
    }

    @Test
    void regradingBeforeAttemptFinalizationIsAllowed() {
        AttemptAnswer answer = answer(8L, QuestionType.SHORT_ANSWER, null);
        answer.setGradingStatus(GradingStatus.GRADED);
        when(answerRepository.findById(8L)).thenReturn(Optional.of(answer));
        when(snapshotRepository.findByQuizIdAndQuestionId(30L, 108L))
                .thenReturn(Optional.of(snapshot(QuestionType.SHORT_ANSWER, null, 4)));
        when(assignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(1L, 20L, 10L))
                .thenReturn(true);
        when(answerRepository.existsPendingShortAnswer(50L)).thenReturn(true);

        GradeAnswerResponse result = service.gradeAnswer(8L, new BigDecimal("3.5"), "Revised");

        assertEquals(new BigDecimal("3.5"), result.marksAwarded());
        assertEquals("Revised", answer.getFacultyFeedback());
        assertFalse(result.attemptFullyGraded());
        verify(answerRepository).save(answer);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void refusesToModifyAnAlreadyFinalizedAttempt() {
        attempt.setStatus(AttemptStatus.GRADED);
        AttemptAnswer answer = answer(9L, QuestionType.SHORT_ANSWER, null);
        when(answerRepository.findById(9L)).thenReturn(Optional.of(answer));
        when(assignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(1L, 20L, 10L))
                .thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.gradeAnswer(9L, BigDecimal.ONE, null));
        verify(answerRepository, never()).save(any());
    }

    private AttemptAnswer answer(Long id, QuestionType type, String selectedOption) {
        Question source = Question.builder().id(100L + id).type(type).text("Live question").build();
        QuizQuestion quizQuestion = QuizQuestion.builder().id(id).quiz(quiz)
                .question(source).marks(1).build();
        return AttemptAnswer.builder().id(id).attempt(attempt).quizQuestion(quizQuestion)
                .selectedOptionKey(selectedOption).gradingStatus(GradingStatus.PENDING).build();
    }

    private QuizQuestionSnapshot snapshot(QuestionType type, String correctKey, int marks) {
        return QuizQuestionSnapshot.builder().quiz(quiz)
                .question(Question.builder().id(101L).type(type).text("Snapshot text").build())
                .questionType(type).snapshotText("Snapshot text")
                .correctOptionKey(correctKey).marks(marks).build();
    }
}
