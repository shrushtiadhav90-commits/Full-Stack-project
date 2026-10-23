package com.nexus.quiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.quiz.dto.faculty.QuizQuestionRequest;
import com.nexus.quiz.dto.faculty.QuizRequest;
import com.nexus.quiz.dto.faculty.QuizDuplicateResponse;
import com.nexus.quiz.entity.*;
import com.nexus.quiz.events.QuizScheduledEvent;
import com.nexus.quiz.exception.ConflictException;
import com.nexus.quiz.exception.ServiceValidationException;
import com.nexus.quiz.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuizServiceTest {

    @Mock private QuizRepository quizRepository;
    @Mock private QuizClassRepository quizClassRepository;
    @Mock private QuizQuestionRepository quizQuestionRepository;
    @Mock private QuizQuestionSnapshotRepository snapshotRepository;
    @Mock private QuizAttemptRepository quizAttemptRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private QuestionOptionRepository optionRepository;
    @Mock private FacultyAssignmentRepository assignmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AttemptFinalizer attemptFinalizer;

    private QuizService service;
    private User faculty;
    private Subject subject;
    private SchoolClass schoolClass;
    private FacultyAssignment assignment;
    private Question question;

    @BeforeEach
    void setUp() {
        faculty = User.builder().id(1L).role(Role.FACULTY).build();
        subject = Subject.builder().id(10L).name("Math").build();
        schoolClass = SchoolClass.builder().id(20L).name("Class 6").section("A").build();
        assignment = FacultyAssignment.builder()
                .faculty(faculty).subject(subject).schoolClass(schoolClass).build();
        question = Question.builder()
                .id(30L).createdBy(faculty).subject(subject).classLevel("Class 6")
                .type(QuestionType.MCQ).text("Question").isActive(true).build();
        Question secondQuestion = Question.builder()
                .id(31L).createdBy(faculty).subject(subject).classLevel("Class 6")
                .type(QuestionType.MCQ).text("Second question").isActive(true).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(faculty));
        when(userRepository.getReferenceById(1L)).thenReturn(faculty);
        when(assignmentRepository.findByFacultyId(1L)).thenReturn(List.of(assignment));
        when(questionRepository.findById(30L)).thenReturn(Optional.of(question));
        when(questionRepository.findById(31L)).thenReturn(Optional.of(secondQuestion));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(invocation -> {
            Quiz quiz = invocation.getArgument(0);
            quiz.setId(100L);
            return quiz;
        });
        when(quizClassRepository.findByQuizIdOrderByIdAsc(anyLong())).thenReturn(List.of());
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(anyLong()))
                .thenReturn(List.of());
        when(optionRepository.findByQuestionIdOrderBySortOrderAscIdAsc(anyLong()))
                .thenReturn(List.of());
        service = new QuizService(quizRepository, quizClassRepository, quizQuestionRepository,
                snapshotRepository, quizAttemptRepository, questionRepository, optionRepository,
                assignmentRepository, userRepository, new ObjectMapper(), eventPublisher, attemptFinalizer);
        authenticate(1L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refusesToCreateQuizForUnassignedSubjectOrClass() {
        when(assignmentRepository.findByFacultyId(1L)).thenReturn(List.of());

        assertThrows(AccessDeniedException.class, () -> service.createQuiz(request(
                10L, List.of(20L), List.of(), null, null)));

        when(assignmentRepository.findByFacultyId(1L)).thenReturn(List.of(assignment));
        assertThrows(AccessDeniedException.class,
                () -> service.createQuiz(request(10L, List.of(99L), List.of(), null, null)));
        verify(quizRepository, never()).save(any());
    }

    @Test
    void calculatesTotalMarksFromQuestionMarksAndStoresOffsetTimesAsUtc() {
        OffsetDateTime start = OffsetDateTime.parse("2026-10-24T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-10-24T11:00:00+05:30");

        var response = service.createQuiz(request(10L, List.of(20L),
                List.of(new QuizQuestionRequest(30L, 3), new QuizQuestionRequest(31L, 4)),
                start, end));

        assertEquals(7, response.totalMarks());
        assertEquals("2026-10-24T10:00:00+05:30", response.startTime());
        ArgumentCaptor<Quiz> quizCaptor = ArgumentCaptor.forClass(Quiz.class);
        verify(quizRepository).save(quizCaptor.capture());
        assertEquals(Instant.parse("2026-10-24T04:30:00Z"), quizCaptor.getValue().getStartTime());
    }

    @Test
    void rejectsEndBeforeStartWithFieldError() {
        OffsetDateTime start = OffsetDateTime.parse("2026-10-24T11:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-10-24T10:00:00+05:30");

        ServiceValidationException exception = assertThrows(ServiceValidationException.class,
                () -> service.createQuiz(request(10L, List.of(20L), List.of(), start, end)));

        assertTrue(exception.getFieldErrors().containsKey("endTime"));
        verify(quizRepository, never()).save(any());
    }

    @Test
    void onlyTheOwningFacultyCanReadEditOrDeleteAQuiz() {
        Quiz other = Quiz.builder()
                .id(60L)
                .createdBy(User.builder().id(2L).build())
                .status(QuizStatus.DRAFT)
                .subject(subject)
                .build();
        when(quizRepository.findById(60L)).thenReturn(Optional.of(other));

        assertThrows(AccessDeniedException.class, () -> service.getQuiz(60L));
        assertThrows(AccessDeniedException.class, () -> service.updateQuiz(60L,
                request(10L, List.of(20L), List.of(), null, null)));
        assertThrows(AccessDeniedException.class, () -> service.deleteQuiz(60L));
        verify(quizRepository, never()).delete(any(Quiz.class));
    }

    @Test
    void scheduledQuizAfterStartRejectsChangesOtherThanEndTime() {
        Quiz scheduled = Quiz.builder()
                .id(60L)
                .createdBy(faculty)
                .subject(subject)
                .status(QuizStatus.SCHEDULED)
                .startTime(Instant.now().minusSeconds(5))
                .endTime(Instant.now().plusSeconds(600))
                .build();
        when(quizRepository.findById(60L)).thenReturn(Optional.of(scheduled));
        when(quizClassRepository.findByQuizIdOrderByIdAsc(60L)).thenReturn(List.of(
                QuizClass.builder().quiz(scheduled).schoolClass(schoolClass).build()));
        when(assignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(1L, 20L, 10L))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> service.updateQuiz(60L,
                request(10L, List.of(20L), List.of(), null, null)));
        verify(quizRepository, never()).save(any());
    }

    @Test
    void publishesQuizWithStableOptionKeysAndSnapshotValues() throws Exception {
        Quiz quiz = publishableQuiz();
        quiz.setShuffleOptions(true);
        question.setModelAnswer("Model answer");
        question.setExplanation("Explanation");
        question.setImageUrl("/api/v1/files/question-images/snapshot.png");
        QuestionOption first = QuestionOption.builder().id(1L).question(question)
                .optionKey("legacy-X").optionText("First").isCorrect(false).sortOrder(0).build();
        QuestionOption second = QuestionOption.builder().id(2L).question(question)
                .optionKey("legacy-Y").optionText("Second").isCorrect(true).sortOrder(1).build();
        when(optionRepository.findByQuestionIdOrderBySortOrderAscIdAsc(30L))
                .thenReturn(List.of(first, second));
        when(snapshotRepository.insertIgnore(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), nullable(String.class), anyInt(), nullable(String.class),
                nullable(String.class), nullable(String.class))).thenReturn(1);

        service.publish(100L);

        ArgumentCaptor<String> snapshotOptions = ArgumentCaptor.forClass(String.class);
        verify(snapshotRepository).insertIgnore(eq(100L), eq(30L), eq("MCQ"), eq("Question"),
                snapshotOptions.capture(), eq("B"), eq(3), eq("Model answer"),
                eq("Explanation"), eq("/api/v1/files/question-images/snapshot.png"));
        assertEquals("[{\"key\":\"A\",\"text\":\"First\"},{\"key\":\"B\",\"text\":\"Second\"}]",
                snapshotOptions.getValue());
        assertEquals(QuizStatus.SCHEDULED, quiz.getStatus());
        verify(eventPublisher).publishEvent(new QuizScheduledEvent(100L, List.of(20L)));
    }

    @Test
    void trueFalseSnapshotsAlwaysUseCanonicalTrueFalseKeysWhenShuffled() {
        publishableQuiz();
        question.setType(QuestionType.TRUE_FALSE);
        when(optionRepository.findByQuestionIdOrderBySortOrderAscIdAsc(30L)).thenReturn(List.of(
                QuestionOption.builder().question(question).optionText("False")
                        .isCorrect(false).sortOrder(0).build(),
                QuestionOption.builder().question(question).optionText("True")
                        .isCorrect(true).sortOrder(1).build()));

        service.publish(100L);

        verify(snapshotRepository).insertIgnore(eq(100L), eq(30L), eq("TRUE_FALSE"), eq("Question"),
                eq("[{\"key\":\"A\",\"text\":\"True\"},{\"key\":\"B\",\"text\":\"False\"}]"),
                eq("A"), eq(3), nullable(String.class), nullable(String.class), nullable(String.class));
    }

    @Test
    void shortAnswerSnapshotsHaveNoOptionsOrCorrectOption() {
        publishableQuiz();
        question.setType(QuestionType.SHORT_ANSWER);

        service.publish(100L);

        verify(snapshotRepository).insertIgnore(eq(100L), eq(30L), eq("SHORT_ANSWER"), eq("Question"),
                eq("[]"), isNull(), eq(3), nullable(String.class), nullable(String.class),
                nullable(String.class));
    }

    @Test
    void publishPreconditionsReturnSpecificBadRequestMessages() {
        Quiz quiz = publishableQuiz();
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(100L))
                .thenReturn(List.of());
        ServiceValidationException noQuestions = assertThrows(ServiceValidationException.class,
                () -> service.publish(100L));
        assertEquals("A quiz must have at least one question before publishing",
                noQuestions.getFieldErrors().get("questions"));

        QuizQuestion qq = QuizQuestion.builder().quiz(quiz).question(question).marks(3).build();
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(100L))
                .thenReturn(List.of(qq));
        when(quizClassRepository.findByQuizIdOrderByIdAsc(100L))
                .thenReturn(List.of(QuizClass.builder().quiz(quiz).schoolClass(schoolClass).build()));
        when(questionRepository.findById(30L)).thenReturn(Optional.of(question));
        question.setIsActive(false);
        ServiceValidationException inactive = assertThrows(ServiceValidationException.class,
                () -> service.publish(100L));
        assertEquals("Every quiz question must still be active",
                inactive.getFieldErrors().get("questions"));
        verify(snapshotRepository, never()).insertIgnore(anyLong(), anyLong(), anyString(),
                anyString(), anyString(), nullable(String.class), anyInt(),
                nullable(String.class), nullable(String.class), nullable(String.class));
    }

    @Test
    void publishingValidatesQuestionSubjectClassesDurationAndTimeWindow() {
        Quiz quiz = publishableQuiz();
        QuizQuestion quizQuestion = QuizQuestion.builder()
                .quiz(quiz).question(question).marks(3).build();
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(100L))
                .thenReturn(List.of(quizQuestion));
        when(quizClassRepository.findByQuizIdOrderByIdAsc(100L)).thenReturn(List.of(
                QuizClass.builder().quiz(quiz).schoolClass(schoolClass).build()));

        question.setSubject(Subject.builder().id(999L).name("Other").build());
        assertPublishField(100L, "questions", "Every quiz question must belong to the quiz subject");
        question.setSubject(subject);

        when(quizClassRepository.findByQuizIdOrderByIdAsc(100L)).thenReturn(List.of());
        assertPublishField(100L, "classIds", "A quiz must target at least one class before publishing");
        when(quizClassRepository.findByQuizIdOrderByIdAsc(100L)).thenReturn(List.of(
                QuizClass.builder().quiz(quiz).schoolClass(schoolClass).build()));

        quiz.setDurationMinutes(0);
        assertPublishField(100L, "durationMinutes", "Quiz duration must be greater than zero");
        quiz.setDurationMinutes(20);
        quiz.setStartTime(Instant.now().minusSeconds(1));
        assertPublishField(100L, "startTime", "Quiz start_time must be in the future");
        quiz.setStartTime(Instant.now().plusSeconds(3600));
        quiz.setEndTime(quiz.getStartTime());
        assertPublishField(100L, "endTime", "Quiz end_time must be after start_time");
    }

    @Test
    void publishingTwiceIsIdempotentForSnapshotsAndEvents() {
        publishableQuiz();
        when(optionRepository.findByQuestionIdOrderBySortOrderAscIdAsc(30L)).thenReturn(List.of(
                QuestionOption.builder().question(question).optionText("Answer").isCorrect(true)
                        .sortOrder(0).build()));

        service.publish(100L);
        service.publish(100L);

        verify(snapshotRepository, times(1)).insertIgnore(anyLong(), anyLong(), anyString(),
                anyString(), anyString(), nullable(String.class), anyInt(),
                nullable(String.class), nullable(String.class), nullable(String.class));
        verify(eventPublisher, times(1)).publishEvent(any(QuizScheduledEvent.class));
    }

    @Test
    void publishingAnUnassignedClassFailsAsValidation() {
        Quiz quiz = publishableQuiz();
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(100L))
                .thenReturn(List.of(QuizQuestion.builder().quiz(quiz).question(question).marks(2).build()));
        when(quizClassRepository.findByQuizIdOrderByIdAsc(100L))
                .thenReturn(List.of(QuizClass.builder().quiz(quiz).schoolClass(schoolClass).build()));
        when(questionRepository.findById(30L)).thenReturn(Optional.of(question));
        when(assignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(1L, 20L, 10L))
                .thenReturn(false);

        ServiceValidationException exception = assertThrows(ServiceValidationException.class,
                () -> service.publish(100L));

        assertTrue(exception.getFieldErrors().get("assignments").contains("faculty assignments"));
        when(assignmentRepository.findByFacultyId(1L)).thenReturn(List.of());
        when(assignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(1L, 20L, 10L))
                .thenReturn(true);
        ServiceValidationException unassignedSubject = assertThrows(ServiceValidationException.class,
                () -> service.publish(100L));
        assertTrue(unassignedSubject.getFieldErrors().get("assignments")
                .contains("faculty assignments"));
        verify(snapshotRepository, never()).insertIgnore(anyLong(), anyLong(), anyString(),
                anyString(), anyString(), nullable(String.class), anyInt(),
                nullable(String.class), nullable(String.class), nullable(String.class));
    }

    @Test
    void scheduledQuizWithAttemptsCanOnlyExtendEndTime() {
        Instant now = Instant.now();
        Quiz quiz = Quiz.builder().id(100L).createdBy(faculty).subject(subject)
                .status(QuizStatus.SCHEDULED).title("Quiz").description("Instructions")
                .durationMinutes(20).startTime(now.minusSeconds(60)).endTime(now.plusSeconds(600))
                .maxAttempts(1).shuffleQuestions(false).shuffleOptions(false)
                .showAnswers(ShowAnswers.AFTER_END).passPercentage(new BigDecimal("40.00"))
                .build();
        when(quizRepository.findById(100L)).thenReturn(Optional.of(quiz));
        when(quizAttemptRepository.existsByQuizId(100L)).thenReturn(true);
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(100L))
                .thenReturn(List.of(QuizQuestion.builder().quiz(quiz).question(question)
                        .sortOrder(0).marks(1).build()));
        when(quizClassRepository.findByQuizIdOrderByIdAsc(100L)).thenReturn(List.of(
                QuizClass.builder().quiz(quiz).schoolClass(schoolClass).build()));
        when(assignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(1L, 20L, 10L))
                .thenReturn(true);
        QuizRequest extended = request(10L, List.of(20L), List.of(new QuizQuestionRequest(30L, 1)),
                OffsetDateTime.ofInstant(quiz.getStartTime(), java.time.ZoneOffset.UTC),
                OffsetDateTime.ofInstant(now.plusSeconds(900), java.time.ZoneOffset.UTC));

        service.updateQuiz(100L, extended);

        assertEquals(now.plusSeconds(900).getEpochSecond(), quiz.getEndTime().getEpochSecond());
        verify(snapshotRepository, never()).deleteAllByQuizId(anyLong());
        verify(snapshotRepository, never()).insertIgnore(anyLong(), anyLong(), anyString(),
                anyString(), anyString(), nullable(String.class), anyInt(),
                nullable(String.class), nullable(String.class), nullable(String.class));
    }

    @Test
    void scheduledQuizBeforeStartWithoutAttemptsRebuildsSnapshots() {
        Instant now = Instant.now();
        Quiz quiz = Quiz.builder().id(100L).createdBy(faculty).subject(subject)
                .status(QuizStatus.SCHEDULED).title("Quiz").description("Instructions")
                .durationMinutes(20).startTime(now.plusSeconds(3600)).endTime(now.plusSeconds(7200))
                .maxAttempts(1).shuffleQuestions(false).shuffleOptions(false)
                .showAnswers(ShowAnswers.AFTER_END).passPercentage(new BigDecimal("40.00"))
                .build();
        QuizQuestion updatedQuestion = QuizQuestion.builder()
                .quiz(quiz).question(question).sortOrder(0).marks(4).build();
        QuizClass quizClass = QuizClass.builder().quiz(quiz).schoolClass(schoolClass).build();
        when(quizRepository.findById(100L)).thenReturn(Optional.of(quiz));
        when(quizAttemptRepository.existsByQuizId(100L)).thenReturn(false);
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(100L))
                .thenReturn(List.of(updatedQuestion), List.of(updatedQuestion));
        when(quizClassRepository.findByQuizIdOrderByIdAsc(100L)).thenReturn(List.of(quizClass));
        when(questionRepository.findById(30L)).thenReturn(Optional.of(question));
        when(assignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(1L, 20L, 10L))
                .thenReturn(true);
        when(optionRepository.findByQuestionIdOrderBySortOrderAscIdAsc(30L)).thenReturn(List.of(
                QuestionOption.builder().question(question).optionText("Answer").isCorrect(true)
                        .sortOrder(0).build()));
        QuizRequest updated = request(10L, List.of(20L), List.of(new QuizQuestionRequest(30L, 4)),
                OffsetDateTime.ofInstant(quiz.getStartTime(), java.time.ZoneOffset.UTC),
                OffsetDateTime.ofInstant(quiz.getEndTime(), java.time.ZoneOffset.UTC));

        service.updateQuiz(100L, updated);

        verify(snapshotRepository).deleteAllByQuizId(100L);
        verify(snapshotRepository).insertIgnore(eq(100L), eq(30L), eq("MCQ"), eq("Question"),
                anyString(), eq("A"), eq(4), isNull(), isNull(), isNull());
    }

    @Test
    void manualCloseAutoSubmitsOnlyInProgressAttemptsAndPublishesCloseEvent() {
        Instant now = Instant.now();
        Quiz quiz = Quiz.builder().id(100L).createdBy(faculty).subject(subject)
                .status(QuizStatus.SCHEDULED).title("Quiz").startTime(now.minusSeconds(30))
                .endTime(now.plusSeconds(600)).build();
        QuizAttempt first = QuizAttempt.builder().id(1L).quiz(quiz).status(AttemptStatus.IN_PROGRESS).build();
        QuizAttempt second = QuizAttempt.builder().id(2L).quiz(quiz).status(AttemptStatus.IN_PROGRESS).build();
        Instant originalEnd = quiz.getEndTime();
        when(quizRepository.findById(100L)).thenReturn(Optional.of(quiz));
        when(quizClassRepository.findByQuizIdOrderByIdAsc(100L)).thenReturn(List.of(
                QuizClass.builder().quiz(quiz).schoolClass(schoolClass).build()));
        when(assignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(1L, 20L, 10L))
                .thenReturn(true);
        when(quizAttemptRepository.findByQuizIdAndStatusOrderByIdAsc(100L, AttemptStatus.IN_PROGRESS))
                .thenReturn(List.of(first, second));

        service.manualClose(100L);

        assertEquals(QuizStatus.CLOSED, quiz.getStatus());
        assertTrue(quiz.getEndTime().isBefore(originalEnd));
        verify(attemptFinalizer).submit(first);
        verify(attemptFinalizer).submit(second);
        verify(attemptFinalizer, times(2)).submit(any(QuizAttempt.class));
        verify(eventPublisher).publishEvent(new com.nexus.quiz.events.QuizClosedEvent(100L));
    }

    @Test
    void manualCloseRejectsNonLiveQuiz() {
        Quiz quiz = Quiz.builder().id(100L).createdBy(faculty).subject(subject)
                .status(QuizStatus.SCHEDULED).startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200)).build();
        when(quizRepository.findById(100L)).thenReturn(Optional.of(quiz));
        when(quizClassRepository.findByQuizIdOrderByIdAsc(100L)).thenReturn(List.of(
                QuizClass.builder().quiz(quiz).schoolClass(schoolClass).build()));
        when(assignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(1L, 20L, 10L))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> service.manualClose(100L));
        verify(eventPublisher, never()).publishEvent(any());
        verify(attemptFinalizer, never()).submit(any());
    }

    @Test
    void facultyCannotPublishCloseOrDuplicateAnotherFacultyMembersQuiz() {
        Quiz other = Quiz.builder().id(60L).createdBy(User.builder().id(2L).build())
                .subject(subject).status(QuizStatus.SCHEDULED)
                .startTime(Instant.now().minusSeconds(30))
                .endTime(Instant.now().plusSeconds(300)).build();
        when(quizRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(other));
        when(quizRepository.findById(60L)).thenReturn(Optional.of(other));

        assertThrows(AccessDeniedException.class, () -> service.publish(60L));
        assertThrows(AccessDeniedException.class, () -> service.manualClose(60L));
        assertThrows(AccessDeniedException.class, () -> service.duplicate(60L));
        verify(quizRepository, never()).save(any(Quiz.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void duplicateCopiesOnlyCurrentAssignmentsAndActiveQuestionsEvenWithAttempts() {
        Quiz source = Quiz.builder().id(60L).createdBy(faculty).subject(subject)
                .status(QuizStatus.CLOSED).title("Original").description("Description")
                .durationMinutes(30).startTime(Instant.now().minusSeconds(7200))
                .endTime(Instant.now().minusSeconds(3600)).maxAttempts(3)
                .shuffleQuestions(true).shuffleOptions(true).showAnswers(ShowAnswers.AFTER_SUBMIT)
                .passPercentage(new BigDecimal("55.00")).totalMarks(5).build();
        SchoolClass unassignedClass = SchoolClass.builder().id(21L).name("Class 7").section("B").build();
        Question inactiveQuestion = Question.builder().id(31L).createdBy(faculty).subject(subject)
                .text("Inactive").isActive(false).build();
        QuizQuestion originalActive = QuizQuestion.builder().quiz(source).question(question)
                .sortOrder(0).marks(3).build();
        QuizQuestion originalInactive = QuizQuestion.builder().quiz(source).question(inactiveQuestion)
                .sortOrder(1).marks(2).build();
        when(quizRepository.findById(60L)).thenReturn(Optional.of(source));
        when(quizRepository.save(any(Quiz.class))).thenAnswer(invocation -> {
            Quiz saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });
        when(quizClassRepository.findByQuizIdOrderByIdAsc(60L)).thenReturn(List.of(
                QuizClass.builder().quiz(source).schoolClass(schoolClass).build(),
                QuizClass.builder().quiz(source).schoolClass(unassignedClass).build()));
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(60L))
                .thenReturn(List.of(originalActive, originalInactive));
        when(questionRepository.findById(30L)).thenReturn(Optional.of(question));
        when(questionRepository.findById(31L)).thenReturn(Optional.of(inactiveQuestion));
        when(quizClassRepository.findByQuizIdOrderByIdAsc(100L)).thenReturn(List.of(
                QuizClass.builder().schoolClass(schoolClass).build()));
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(100L)).thenReturn(List.of());

        QuizDuplicateResponse response = service.duplicate(60L, "New title");

        ArgumentCaptor<Quiz> copyCaptor = ArgumentCaptor.forClass(Quiz.class);
        verify(quizRepository).save(copyCaptor.capture());
        Quiz copy = copyCaptor.getValue();
        assertEquals("New title", copy.getTitle());
        assertEquals("Description", copy.getDescription());
        assertEquals(QuizStatus.DRAFT, copy.getStatus());
        assertNull(copy.getStartTime());
        assertNull(copy.getEndTime());
        assertEquals(30, copy.getDurationMinutes());
        assertTrue(copy.getShuffleQuestions());
        assertEquals(0, copy.getPassPercentage().compareTo(new BigDecimal("55.00")));
        assertEquals(1, response.quiz().classes().size());
        assertEquals(List.of(31L), response.skippedQuestions().stream()
                .map(item -> item.questionId()).toList());
        assertEquals(List.of(21L), response.skippedClasses().stream()
                .map(item -> item.classId()).toList());
        verify(quizAttemptRepository, never()).existsByQuizId(60L);
    }

    private void assertPublishField(Long quizId, String field, String expectedMessage) {
        ServiceValidationException exception = assertThrows(ServiceValidationException.class,
                () -> service.publish(quizId));
        assertEquals(expectedMessage, exception.getFieldErrors().get(field));
    }

    private Quiz publishableQuiz() {
        Quiz quiz = Quiz.builder().id(100L).createdBy(faculty).subject(subject)
                .status(QuizStatus.DRAFT).title("Quiz").durationMinutes(20)
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200)).build();
        when(quizRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(quiz));
        when(quizClassRepository.findByQuizIdOrderByIdAsc(100L)).thenReturn(List.of(
                QuizClass.builder().quiz(quiz).schoolClass(schoolClass).build()));
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(100L)).thenReturn(List.of(
                QuizQuestion.builder().quiz(quiz).question(question).marks(3).build()));
        when(questionRepository.findById(30L)).thenReturn(Optional.of(question));
        when(assignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(1L, 20L, 10L))
                .thenReturn(true);
        return quiz;
    }

    @Test
    void blocksAnotherFacultysQuestionWhenBuildingAQuiz() {
        question.setCreatedBy(User.builder().id(2L).build());

        assertThrows(AccessDeniedException.class, () -> service.createQuiz(request(
                10L, List.of(20L), List.of(new QuizQuestionRequest(30L, 2)), null, null)));
        verify(quizRepository, never()).save(any());
    }

    private QuizRequest request(
            Long subjectId,
            List<Long> classIds,
            List<QuizQuestionRequest> questions,
            OffsetDateTime start,
            OffsetDateTime end) {
        return new QuizRequest("Quiz", "Instructions", subjectId, classIds, questions,
                20, start, end, 1, false, false, ShowAnswers.AFTER_END,
                new BigDecimal("40.00"));
    }

    private void authenticate(Long id) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(id, null, List.of()));
    }
}
