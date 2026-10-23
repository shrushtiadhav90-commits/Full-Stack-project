package com.nexus.quiz.service;

import com.nexus.quiz.dto.admin.PageResponse;
import com.nexus.quiz.dto.faculty.*;
import com.nexus.quiz.entity.*;
import com.nexus.quiz.exception.ConflictException;
import com.nexus.quiz.exception.ResourceNotFoundException;
import com.nexus.quiz.exception.ServiceValidationException;
import com.nexus.quiz.repository.*;
import com.nexus.quiz.util.DateTimeUtil;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class QuizService {

    private static final int MAX_PAGE_SIZE = 100;
    private final QuizRepository quizRepository;
    private final QuizClassRepository quizClassRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final FacultyAssignmentRepository facultyAssignmentRepository;
    private final UserRepository userRepository;

    public QuizService(
            QuizRepository quizRepository,
            QuizClassRepository quizClassRepository,
            QuizQuestionRepository quizQuestionRepository,
            QuizAttemptRepository quizAttemptRepository,
            QuestionRepository questionRepository,
            QuestionOptionRepository optionRepository,
            FacultyAssignmentRepository facultyAssignmentRepository,
            UserRepository userRepository) {
        this.quizRepository = quizRepository;
        this.quizClassRepository = quizClassRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.facultyAssignmentRepository = facultyAssignmentRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<FacultyAssignmentOptionResponse> listMyAssignments() {
        Long facultyId = requireFacultyId();
        return facultyAssignmentRepository.findByFacultyId(facultyId).stream()
                .map(assignment -> new FacultyAssignmentOptionResponse(
                        assignment.getSchoolClass().getId(),
                        assignment.getSchoolClass().getName(),
                        assignment.getSchoolClass().getSection(),
                        assignment.getSubject().getId(),
                        assignment.getSubject().getName()))
                .toList();
    }

    @Transactional
    public QuizResponse createQuiz(QuizRequest request) {
        Long facultyId = requireFacultyId();
        validateRequest(request);
        List<FacultyAssignment> assignments = validateAssignments(facultyId, request);
        Subject subject = assignments.getFirst().getSubject();
        Map<Long, SchoolClass> targetClasses = targetClasses(assignments, request.classIds());
        List<Question> selectedQuestions = validateQuestions(facultyId, subject, targetClasses, request);
        int totalMarks = totalMarks(request);

        Quiz quiz = Quiz.builder()
                .title(request.title().trim())
                .description(normalize(request.description()))
                .subject(subject)
                .createdBy(userRepository.getReferenceById(facultyId))
                .durationMinutes(request.durationMinutes())
                .startTime(toInstant(request.startTime()))
                .endTime(toInstant(request.endTime()))
                .maxAttempts(request.maxAttempts())
                .shuffleQuestions(request.shuffleQuestions())
                .shuffleOptions(request.shuffleOptions())
                .showAnswers(request.showAnswers())
                .passPercentage(request.passPercentage())
                .totalMarks(totalMarks)
                .status(QuizStatus.DRAFT)
                .build();
        Quiz saved = quizRepository.save(quiz);
        saveQuizClasses(saved, targetClasses);
        saveQuizQuestions(saved, request.questions(), selectedQuestions);
        return toResponse(saved);
    }

    @Transactional
    public QuizResponse updateQuiz(Long quizId, QuizRequest request) {
        Long facultyId = requireFacultyId();
        validateRequest(request);
        Quiz quiz = requireOwnedQuiz(quizId, facultyId);
        if (quiz.getStatus() != QuizStatus.DRAFT) {
            throw new ConflictException("Only DRAFT quizzes can be edited in this phase");
        }
        List<FacultyAssignment> assignments = validateAssignments(facultyId, request);
        Subject subject = assignments.getFirst().getSubject();
        Map<Long, SchoolClass> targetClasses = targetClasses(assignments, request.classIds());
        List<Question> selectedQuestions = validateQuestions(facultyId, subject, targetClasses, request);
        int totalMarks = totalMarks(request);

        quiz.setTitle(request.title().trim());
        quiz.setDescription(normalize(request.description()));
        quiz.setSubject(subject);
        quiz.setDurationMinutes(request.durationMinutes());
        quiz.setStartTime(toInstant(request.startTime()));
        quiz.setEndTime(toInstant(request.endTime()));
        quiz.setMaxAttempts(request.maxAttempts());
        quiz.setShuffleQuestions(request.shuffleQuestions());
        quiz.setShuffleOptions(request.shuffleOptions());
        quiz.setShowAnswers(request.showAnswers());
        quiz.setPassPercentage(request.passPercentage());
        quiz.setTotalMarks(totalMarks);
        quizRepository.save(quiz);

        quizClassRepository.deleteAllByQuizId(quizId);
        quizQuestionRepository.deleteAllByQuizId(quizId);
        saveQuizClasses(quiz, targetClasses);
        saveQuizQuestions(quiz, request.questions(), selectedQuestions);
        return toResponse(quiz);
    }

    @Transactional
    public void deleteQuiz(Long quizId) {
        Long facultyId = requireFacultyId();
        Quiz quiz = requireOwnedQuiz(quizId, facultyId);
        if (quiz.getStatus() != QuizStatus.DRAFT) {
            throw new ConflictException("Only DRAFT quizzes can be deleted");
        }
        if (quizAttemptRepository.existsByQuizId(quizId)) {
            throw new ConflictException("A quiz with attempts cannot be deleted");
        }
        quizRepository.delete(quiz);
    }

    @Transactional(readOnly = true)
    public QuizResponse getQuiz(Long quizId) {
        Long facultyId = requireFacultyId();
        return toResponse(requireOwnedQuiz(quizId, facultyId));
    }

    @Transactional(readOnly = true)
    public PageResponse<QuizResponse> listMyQuizzes(
            int page,
            int size,
            Quiz.EffectiveStatus status,
            Long subjectId,
            Long classId) {
        Long facultyId = requireFacultyId();
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100");
        }
        Instant now = Instant.now();
        List<QuizResponse> filtered = quizRepository.findByCreatedByIdOrderByCreatedAtDescIdDesc(facultyId)
                .stream()
                .filter(quiz -> hasCurrentAssignments(quiz, facultyId))
                .filter(quiz -> subjectId == null || quiz.getSubject().getId().equals(subjectId))
                .filter(quiz -> status == null || quiz.effectiveStatusAt(now) == status)
                .filter(quiz -> classId == null || quizClassRepository.findByQuizIdOrderByIdAsc(quiz.getId())
                        .stream().anyMatch(quizClass -> quizClass.getSchoolClass().getId().equals(classId)))
                .map(this::toResponse)
                .toList();
        int start = (int) Math.min((long) page * size, filtered.size());
        int end = Math.min(start + size, filtered.size());
        return new PageResponse<>(filtered.subList(start, end), page, size,
                filtered.size(), (filtered.size() + size - 1) / size);
    }

    private Long requireFacultyId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Number principalId)) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated faculty user is required");
        }
        Long facultyId = principalId.longValue();
        User faculty = userRepository.findById(facultyId)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "Authenticated faculty user was not found"));
        if (faculty.getRole() != Role.FACULTY) {
            throw new AccessDeniedException("Faculty access is required");
        }
        return facultyId;
    }

    private Quiz requireOwnedQuiz(Long quizId, Long facultyId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));
        if (!facultyId.equals(quiz.getCreatedBy().getId())) {
            throw new AccessDeniedException("Faculty may only access their own quizzes");
        }
        List<QuizClass> classes = quizClassRepository.findByQuizIdOrderByIdAsc(quizId);
        if (classes.isEmpty() || classes.stream().anyMatch(quizClass ->
                !facultyAssignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(
                        facultyId, quizClass.getSchoolClass().getId(), quiz.getSubject().getId()))) {
            throw new AccessDeniedException("Faculty is no longer assigned to this quiz");
        }
        return quiz;
    }

    private boolean hasCurrentAssignments(Quiz quiz, Long facultyId) {
        List<QuizClass> classes = quizClassRepository.findByQuizIdOrderByIdAsc(quiz.getId());
        return !classes.isEmpty() && classes.stream().allMatch(quizClass ->
                facultyAssignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(
                        facultyId, quizClass.getSchoolClass().getId(), quiz.getSubject().getId()));
    }

    private void validateRequest(QuizRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Quiz request is required");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw fieldError(request, "title", "Title is required");
        }
        if (request.title().length() > 200) {
            throw fieldError(request, "title", "Title must not exceed 200 characters");
        }
        if (request.subjectId() == null) {
            throw fieldError(request, "subjectId", "Subject is required");
        }
        if (request.classIds() == null || request.classIds().isEmpty()) {
            throw fieldError(request, "classIds", "At least one target class is required");
        }
        if (request.classIds().stream().anyMatch(java.util.Objects::isNull)
                || request.classIds().size() != new HashSet<>(request.classIds()).size()) {
            throw fieldError(request, "classIds", "Target class ids must be present and unique");
        }
        if (request.durationMinutes() == null || request.durationMinutes() <= 0) {
            throw fieldError(request, "durationMinutes", "Duration must be positive");
        }
        if (request.maxAttempts() == null || request.maxAttempts() <= 0) {
            throw fieldError(request, "maxAttempts", "Maximum attempts must be positive");
        }
        if (request.shuffleQuestions() == null || request.shuffleOptions() == null
                || request.showAnswers() == null || request.passPercentage() == null) {
            throw fieldError(request, "settings", "Quiz settings are required");
        }
        if (request.passPercentage().compareTo(BigDecimal.ZERO) < 0
                || request.passPercentage().compareTo(new BigDecimal("100")) > 0) {
            throw fieldError(request, "passPercentage", "Pass percentage must be from 0 to 100");
        }
        if (request.startTime() != null && request.endTime() != null
                && !request.endTime().isAfter(request.startTime())) {
            throw fieldError(request, "endTime", "End time must be after start time");
        }
        List<QuizQuestionRequest> questions =
                request.questions() == null ? List.of() : request.questions();
        if (questions.stream().anyMatch(question -> question.questionId() == null
                || question.marks() == null || question.marks() <= 0)) {
            throw fieldError(request, "questions", "Each question requires an id and positive marks");
        }
        List<Long> questionIds = questions.stream().map(QuizQuestionRequest::questionId).toList();
        if (questionIds.size() != new HashSet<>(questionIds).size()) {
            throw fieldError(request, "questions", "Question ids must be unique");
        }
    }

    private List<FacultyAssignment> validateAssignments(Long facultyId, QuizRequest request) {
        List<FacultyAssignment> assignments = facultyAssignmentRepository.findByFacultyId(facultyId)
                .stream()
                .filter(assignment -> assignment.getSubject().getId().equals(request.subjectId()))
                .toList();
        if (assignments.isEmpty()) {
            throw new AccessDeniedException("Faculty is not assigned to this subject");
        }
        Set<Long> assignedClassIds = assignments.stream()
                .map(assignment -> assignment.getSchoolClass().getId())
                .collect(java.util.stream.Collectors.toSet());
        if (!assignedClassIds.containsAll(request.classIds())) {
            throw new AccessDeniedException("Faculty is not assigned to every selected class and subject");
        }
        return assignments;
    }

    private Map<Long, SchoolClass> targetClasses(
            List<FacultyAssignment> assignments, List<Long> targetClassIds) {
        Map<Long, SchoolClass> available = assignments.stream()
                .map(FacultyAssignment::getSchoolClass)
                .filter(schoolClass -> targetClassIds.contains(schoolClass.getId()))
                .collect(java.util.stream.Collectors.toMap(
                        SchoolClass::getId, schoolClass -> schoolClass, (first, ignored) -> first));
        if (available.size() != targetClassIds.size()) {
            throw new AccessDeniedException("Faculty is not assigned to every selected class and subject");
        }
        return available;
    }

    private List<Question> validateQuestions(
            Long facultyId,
            Subject subject,
            Map<Long, SchoolClass> targetClasses,
            QuizRequest request) {
        List<QuizQuestionRequest> requests =
                request.questions() == null ? List.of() : request.questions();
        return requests.stream().map(item -> {
            Question question = questionRepository.findById(item.questionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Question not found"));
            if (!facultyId.equals(question.getCreatedBy().getId())) {
                throw new AccessDeniedException("Faculty may only add their own questions to quizzes");
            }
            if (!Boolean.TRUE.equals(question.getIsActive())) {
                throw new ResourceNotFoundException("Question not found");
            }
            if (!subject.getId().equals(question.getSubject().getId())) {
                throw fieldError(request, "questions", "All quiz questions must use the selected subject");
            }
            boolean classLevelMatches = targetClasses.values().stream()
                    .map(SchoolClass::getName)
                    .anyMatch(name -> name.equalsIgnoreCase(question.getClassLevel()));
            if (!classLevelMatches) {
                throw fieldError(request, "questions", "Question class level must match a target class");
            }
            return question;
        }).toList();
    }

    private int totalMarks(QuizRequest request) {
        int total = 0;
        if (request.questions() != null) {
            for (QuizQuestionRequest question : request.questions()) {
                try {
                    total = Math.addExact(total, question.marks());
                } catch (ArithmeticException exception) {
                    throw fieldError(request, "questions", "Total marks exceed the supported range");
                }
            }
        }
        return total;
    }

    private void saveQuizClasses(Quiz quiz, Map<Long, SchoolClass> targetClasses) {
        quizClassRepository.saveAll(targetClasses.values().stream()
                .map(schoolClass -> QuizClass.builder().quiz(quiz).schoolClass(schoolClass).build())
                .toList());
    }

    private void saveQuizQuestions(
            Quiz quiz, List<QuizQuestionRequest> requests, List<Question> selectedQuestions) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        for (int index = 0; index < requests.size(); index++) {
            quizQuestionRepository.save(QuizQuestion.builder()
                    .quiz(quiz)
                    .question(selectedQuestions.get(index))
                    .sortOrder(index)
                    .marks(requests.get(index).marks())
                    .build());
        }
    }

    private QuizResponse toResponse(Quiz quiz) {
        List<QuizClassResponse> classes = quizClassRepository.findByQuizIdOrderByIdAsc(quiz.getId())
                .stream()
                .map(quizClass -> new QuizClassResponse(
                        quizClass.getSchoolClass().getId(),
                        quizClass.getSchoolClass().getName(),
                        quizClass.getSchoolClass().getSection()))
                .toList();
        List<QuizQuestionResponse> questions =
                quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(quiz.getId())
                        .stream()
                        .map(this::toQuestionResponse)
                        .toList();
        return new QuizResponse(
                quiz.getId(),
                quiz.getTitle(),
                quiz.getDescription(),
                quiz.getSubject().getId(),
                quiz.getSubject().getName(),
                quiz.getCreatedBy().getId(),
                quiz.getDurationMinutes(),
                DateTimeUtil.toIstString(quiz.getStartTime()),
                DateTimeUtil.toIstString(quiz.getEndTime()),
                quiz.getMaxAttempts(),
                Boolean.TRUE.equals(quiz.getShuffleQuestions()),
                Boolean.TRUE.equals(quiz.getShuffleOptions()),
                quiz.getShowAnswers(),
                quiz.getPassPercentage(),
                quiz.getTotalMarks(),
                quiz.effectiveStatusAt(Instant.now()),
                quiz.getCreatedAt(),
                quiz.getUpdatedAt(),
                classes,
                questions);
    }

    private QuizQuestionResponse toQuestionResponse(QuizQuestion quizQuestion) {
        Question question = quizQuestion.getQuestion();
        List<QuestionOptionResponse> options =
                optionRepository.findByQuestionIdOrderBySortOrderAscIdAsc(question.getId())
                        .stream()
                        .map(option -> new QuestionOptionResponse(
                                option.getId(),
                                option.getOptionKey(),
                                option.getOptionText(),
                                Boolean.TRUE.equals(option.getIsCorrect()),
                                option.getSortOrder()))
                        .toList();
        return new QuizQuestionResponse(
                quizQuestion.getId(),
                question.getId(),
                quizQuestion.getSortOrder(),
                quizQuestion.getMarks(),
                question.getClassLevel(),
                question.getTopic(),
                question.getType().name(),
                question.getText(),
                question.getImageUrl(),
                options);
    }

    private ServiceValidationException fieldError(Object target, String field, String message) {
        return new ServiceValidationException(field, message);
    }

    private Instant toInstant(java.time.OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
