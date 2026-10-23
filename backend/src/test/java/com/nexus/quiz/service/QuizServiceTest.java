package com.nexus.quiz.service;

import com.nexus.quiz.dto.faculty.QuizQuestionRequest;
import com.nexus.quiz.dto.faculty.QuizRequest;
import com.nexus.quiz.entity.*;
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
    @Mock private QuizAttemptRepository quizAttemptRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private QuestionOptionRepository optionRepository;
    @Mock private FacultyAssignmentRepository assignmentRepository;
    @Mock private UserRepository userRepository;

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
                quizAttemptRepository, questionRepository, optionRepository,
                assignmentRepository, userRepository);
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
    void refusesToEditNonDraftQuizInThisPhase() {
        Quiz scheduled = Quiz.builder()
                .id(60L)
                .createdBy(faculty)
                .subject(subject)
                .status(QuizStatus.SCHEDULED)
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
