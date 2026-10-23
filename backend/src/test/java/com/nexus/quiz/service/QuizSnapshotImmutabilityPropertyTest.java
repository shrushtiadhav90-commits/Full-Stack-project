package com.nexus.quiz.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.quiz.dto.faculty.QuestionOptionRequest;
import com.nexus.quiz.dto.faculty.QuestionRequest;
import com.nexus.quiz.entity.*;
import com.nexus.quiz.repository.*;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Provide;
import net.jqwik.api.Property;
import net.jqwik.api.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuizSnapshotImmutabilityPropertyTest {

    @Provide
    Arbitrary<List<Integer>> questionEdits() {
        return Arbitraries.integers().between(1, 1_000_000).list().ofMinSize(1).ofMaxSize(20);
    }

    @Property(tries = 100)
    @Tag("Feature-nexus-quiz-portal-Property-2-Snapshot-Immutability")
    void sourceQuestionEditsNeverChangeThePublishedSnapshot(@ForAll("questionEdits") List<Integer> edits)
            throws Exception {
        User faculty = User.builder().id(1L).role(Role.FACULTY).build();
        Subject subject = Subject.builder().id(10L).name("Math").build();
        SchoolClass schoolClass = SchoolClass.builder().id(20L).name("Class 6").section("A").build();
        FacultyAssignment assignment = FacultyAssignment.builder().faculty(faculty)
                .subject(subject).schoolClass(schoolClass).build();
        Question question = Question.builder().id(30L).createdBy(faculty).subject(subject)
                .classLevel("Class 6").type(QuestionType.MCQ).text("Original text")
                .modelAnswer("Original answer").explanation("Original explanation")
                .marks(2).isActive(true).build();
        Quiz quiz = Quiz.builder().id(100L).createdBy(faculty).subject(subject)
                .status(QuizStatus.DRAFT).title("Quiz").durationMinutes(20)
                .startTime(Instant.now().plusSeconds(3600))
                .endTime(Instant.now().plusSeconds(7200)).build();
        QuizQuestion quizQuestion = QuizQuestion.builder().quiz(quiz)
                .question(question).sortOrder(0).marks(3).build();
        QuizClass quizClass = QuizClass.builder().quiz(quiz).schoolClass(schoolClass).build();

        QuizRepository quizRepository = mock(QuizRepository.class);
        QuizClassRepository quizClassRepository = mock(QuizClassRepository.class);
        QuizQuestionRepository quizQuestionRepository = mock(QuizQuestionRepository.class);
        QuizQuestionSnapshotRepository snapshotRepository = mock(QuizQuestionSnapshotRepository.class);
        QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);
        QuestionRepository questionRepository = mock(QuestionRepository.class);
        QuestionOptionRepository optionRepository = mock(QuestionOptionRepository.class);
        FacultyAssignmentRepository assignmentRepository = mock(FacultyAssignmentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        AttemptFinalizer attemptFinalizer = mock(AttemptFinalizer.class);

        when(quizRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(quiz));
        when(quizClassRepository.findByQuizIdOrderByIdAsc(100L)).thenReturn(List.of(quizClass));
        when(quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(100L))
                .thenReturn(List.of(quizQuestion));
        when(questionRepository.findById(30L)).thenReturn(Optional.of(question));
        when(assignmentRepository.findByFacultyId(1L)).thenReturn(List.of(assignment));
        when(assignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(1L, 20L, 10L))
                .thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(faculty));
        when(optionRepository.findByQuestionIdOrderBySortOrderAscIdAsc(30L)).thenReturn(List.of(
                QuestionOption.builder().question(question).optionKey("old-A").optionText("North")
                        .isCorrect(true).sortOrder(0).build(),
                QuestionOption.builder().question(question).optionKey("old-B").optionText("South")
                        .isCorrect(false).sortOrder(1).build()));
        when(optionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AtomicReference<QuizQuestionSnapshot> publishedSnapshot = new AtomicReference<>();
        ObjectMapper objectMapper = new ObjectMapper();
        doAnswer(invocation -> {
            publishedSnapshot.set(QuizQuestionSnapshot.builder()
                    .id(1L)
                    .quiz(quiz)
                    .question(question)
                    .questionType(QuestionType.valueOf(invocation.getArgument(2)))
                    .snapshotText(invocation.getArgument(3))
                    .snapshotOptions(objectMapper.readValue(
                            (String) invocation.getArgument(4),
                            new TypeReference<List<Map<String, String>>>() {}))
                    .correctOptionKey(invocation.getArgument(5))
                    .marks(invocation.getArgument(6))
                    .modelAnswer(invocation.getArgument(7))
                    .explanation(invocation.getArgument(8))
                    .imageUrl(invocation.getArgument(9))
                    .createdAt(Instant.EPOCH)
                    .build());
            return 1;
        }).when(snapshotRepository).insertIgnore(
                anyLong(), anyLong(), anyString(), anyString(), anyString(),
                nullable(String.class), anyInt(), nullable(String.class),
                nullable(String.class), nullable(String.class));
        when(snapshotRepository.findByQuizIdOrderByIdAsc(100L))
                .thenAnswer(invocation -> List.of(publishedSnapshot.get()));

        QuizService quizService = new QuizService(
                quizRepository, quizClassRepository, quizQuestionRepository, snapshotRepository,
                attemptRepository, questionRepository, optionRepository, assignmentRepository,
                userRepository, new ObjectMapper(), eventPublisher, attemptFinalizer);
        QuestionService questionService = new QuestionService(
                questionRepository, optionRepository, snapshotRepository, assignmentRepository,
                userRepository, mock(FileStorageService.class));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of()));
        try {
            quizService.publish(100L);
            SnapshotBytes originalSnapshot = snapshotBytes(
                    snapshotRepository.findByQuizIdOrderByIdAsc(100L).getFirst(), objectMapper);

            int iteration = 0;
            for (Integer edit : edits) {
                QuestionRequest request = new QuestionRequest(
                        "Edited question " + edit,
                        QuestionType.MCQ,
                        10L,
                        "Class 6",
                        "topic-" + edit,
                        QuestionDifficulty.MEDIUM,
                        edit % 100 + 1,
                        "explanation-" + edit,
                        "answer-" + edit,
                        List.of(
                                new QuestionOptionRequest("A", "option-a-" + edit, edit % 2 == 0, 0),
                                new QuestionOptionRequest("B", "option-b-" + edit, edit % 2 != 0, 1)),
                        false);
                questionService.updateQuestion(30L, request, null);
                iteration++;
            }
            questionService.deleteQuestion(30L);

            assertEquals(originalSnapshot, snapshotBytes(
                    snapshotRepository.findByQuizIdOrderByIdAsc(100L).getFirst(), objectMapper));
            assertEquals(edits.size(), iteration);
            verify(snapshotRepository, times(1)).insertIgnore(
                    anyLong(), anyLong(), anyString(), anyString(), anyString(),
                    nullable(String.class), anyInt(), nullable(String.class),
                    nullable(String.class), nullable(String.class));
            verify(snapshotRepository, never()).deleteAllByQuizId(anyLong());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private SnapshotBytes snapshotBytes(
            QuizQuestionSnapshot snapshot, ObjectMapper objectMapper) throws Exception {
        return new SnapshotBytes(
                snapshot.getId(),
                snapshot.getQuiz().getId(),
                snapshot.getQuestion().getId(),
                snapshot.getQuestionType(),
                snapshot.getSnapshotText(),
                objectMapper.writeValueAsString(snapshot.getSnapshotOptions()),
                snapshot.getCorrectOptionKey(),
                snapshot.getMarks(),
                snapshot.getModelAnswer(),
                snapshot.getExplanation(),
                snapshot.getImageUrl(),
                snapshot.getCreatedAt());
    }

    private record SnapshotBytes(
            Long id,
            Long quizId,
            Long questionId,
            QuestionType questionType,
            String text,
            String options,
            String correctOptionKey,
            Integer marks,
            String modelAnswer,
            String explanation,
            String imageUrl,
            Instant createdAt) {
    }
}
