package com.nexus.quiz.service;

import com.nexus.quiz.dto.faculty.QuestionOptionRequest;
import com.nexus.quiz.dto.faculty.QuestionRequest;
import com.nexus.quiz.entity.*;
import com.nexus.quiz.exception.ServiceValidationException;
import com.nexus.quiz.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuestionServiceTest {

    @Mock private QuestionRepository questionRepository;
    @Mock private QuestionOptionRepository optionRepository;
    @Mock private QuizQuestionSnapshotRepository snapshotRepository;
    @Mock private FacultyAssignmentRepository assignmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private FileStorageService fileStorageService;

    private QuestionService service;
    private User faculty;
    private Subject subject;
    private SchoolClass schoolClass;
    private FacultyAssignment assignment;

    @BeforeEach
    void setUp() {
        faculty = User.builder().id(1L).role(Role.FACULTY).build();
        subject = Subject.builder().id(10L).name("Math").build();
        schoolClass = SchoolClass.builder().id(20L).name("Class 6").section("A").build();
        assignment = FacultyAssignment.builder()
                .faculty(faculty).subject(subject).schoolClass(schoolClass).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(faculty));
        when(userRepository.getReferenceById(1L)).thenReturn(faculty);
        when(assignmentRepository.findByFacultyId(1L)).thenReturn(List.of(assignment));
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(100L);
            return question;
        });
        when(optionRepository.findByQuestionIdOrderBySortOrderAscIdAsc(anyLong()))
                .thenReturn(List.of());
        service = new QuestionService(questionRepository, optionRepository, snapshotRepository,
                assignmentRepository, userRepository, fileStorageService);
        authenticate(1L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refusesQuestionForUnassignedSubjectOrClassLevel() {
        when(assignmentRepository.findByFacultyId(1L)).thenReturn(List.of());

        assertThrows(AccessDeniedException.class,
                () -> service.createQuestion(question(QuestionType.SHORT_ANSWER, List.of()), null));
        verify(questionRepository, never()).save(any());
    }

    @Test
    void acceptsMcqWithExactlyOneCorrectOption() {
        var result = service.createQuestion(question(QuestionType.MCQ, List.of(
                option("A", "One", true), option("B", "Two", false))), null);

        assertEquals(QuestionType.MCQ, result.type());
        ArgumentCaptor<List<QuestionOption>> options = ArgumentCaptor.forClass(List.class);
        verify(optionRepository).saveAll(options.capture());
        assertEquals(List.of("A", "B"),
                options.getValue().stream().map(QuestionOption::getOptionKey).toList());
    }

    @Test
    void rejectsMcqWithoutExactlyOneCorrectOption() {
        QuestionRequest invalid = question(QuestionType.MCQ, List.of(
                option("A", "One", false), option("B", "Two", false)));

        ServiceValidationException exception = assertThrows(ServiceValidationException.class,
                () -> service.createQuestion(invalid, null));

        assertTrue(exception.getFieldErrors().containsKey("options"));
        verify(questionRepository, never()).save(any());
    }

    @Test
    void acceptsTrueFalseOnlyWithTrueAndFalseAndOneCorrect() {
        assertDoesNotThrow(() -> service.createQuestion(question(QuestionType.TRUE_FALSE, List.of(
                option("A", "True", true), option("B", "False", false))), null));

        assertThrows(ServiceValidationException.class, () -> service.createQuestion(
                question(QuestionType.TRUE_FALSE, List.of(
                        option("A", "Yes", true), option("B", "No", false))), null));
    }

    @Test
    void acceptsShortAnswerWithoutOptions() {
        var result = service.createQuestion(question(QuestionType.SHORT_ANSWER, List.of()), null);

        assertEquals(QuestionType.SHORT_ANSWER, result.type());
        verify(optionRepository).saveAll(argThat(options -> !options.iterator().hasNext()));
    }

    @Test
    void blocksReadingEditingAndDeletingAnotherFacultysQuestion() {
        Question other = questionEntity(2L);
        when(questionRepository.findById(50L)).thenReturn(Optional.of(other));

        assertThrows(AccessDeniedException.class, () -> service.getQuestion(50L));
        assertThrows(AccessDeniedException.class,
                () -> service.updateQuestion(50L, question(QuestionType.MCQ, List.of(
                        option("A", "One", true), option("B", "Two", false))), null));
        assertThrows(AccessDeniedException.class, () -> service.deleteQuestion(50L));
        verify(questionRepository, never()).delete(any(Question.class));
        verify(questionRepository, never()).save(any());
    }

    @Test
    void softDeletePreservesQuestionAndItsImageReferences() throws Exception {
        Question question = questionEntity(1L);
        question.setId(50L);
        question.setImageUrl("/api/v1/files/question-images/source.png");
        when(questionRepository.findById(50L)).thenReturn(Optional.of(question));

        service.deleteQuestion(50L);

        assertFalse(question.getIsActive());
        verify(questionRepository).save(question);
        verify(fileStorageService, never()).delete(anyString(), anyString());
    }

    @Test
    void doesNotDeleteReplacedImageReferencedBySnapshot() throws Exception {
        Question question = questionEntity(1L);
        question.setId(50L);
        String oldImage = "/api/v1/files/question-images/source.png";
        question.setImageUrl(oldImage);
        when(questionRepository.findById(50L)).thenReturn(Optional.of(question));
        when(fileStorageService.store(any(MultipartFile.class), eq("question-images")))
                .thenReturn("replacement.png");
        when(snapshotRepository.existsByImageUrl(oldImage)).thenReturn(true);
        MockMultipartFile image = new MockMultipartFile(
                "image", "new.png", "image/png", pngBytes());

        var result = service.updateQuestion(50L,
                question(QuestionType.MCQ, List.of(
                        option("A", "One", true), option("B", "Two", false))), image);

        assertEquals("/api/v1/files/question-images/replacement.png", result.imageUrl());
        verify(snapshotRepository).existsByImageUrl(oldImage);
        verify(fileStorageService, never()).delete("question-images", "source.png");
    }

    private QuestionRequest question(QuestionType type, List<QuestionOptionRequest> options) {
        return new QuestionRequest("Question text", type, 10L, "Class 6",
                "Topic", QuestionDifficulty.EASY, 2, null, "Model", options, false);
    }

    private QuestionOptionRequest option(String key, String text, boolean correct) {
        return new QuestionOptionRequest(key, text, correct, null);
    }

    private Question questionEntity(Long ownerId) {
        return Question.builder()
                .createdBy(User.builder().id(ownerId).role(Role.FACULTY).build())
                .subject(subject)
                .classLevel("Class 6")
                .type(QuestionType.MCQ)
                .text("Question")
                .difficulty(QuestionDifficulty.EASY)
                .marks(1)
                .isActive(true)
                .build();
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private void authenticate(Long id) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(id, null, List.of()));
    }
}
