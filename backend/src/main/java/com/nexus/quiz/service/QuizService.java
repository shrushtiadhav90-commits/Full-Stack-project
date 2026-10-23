package com.nexus.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.quiz.dto.admin.PageResponse;
import com.nexus.quiz.dto.faculty.*;
import com.nexus.quiz.entity.*;
import com.nexus.quiz.events.QuizClosedEvent;
import com.nexus.quiz.events.QuizScheduledEvent;
import com.nexus.quiz.exception.ConflictException;
import com.nexus.quiz.exception.ResourceNotFoundException;
import com.nexus.quiz.exception.ServiceValidationException;
import com.nexus.quiz.repository.*;
import com.nexus.quiz.util.DateTimeUtil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class QuizService {

    private static final int MAX_PAGE_SIZE = 100;
    private final QuizRepository quizRepository;
    private final QuizClassRepository quizClassRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizQuestionSnapshotRepository snapshotRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final FacultyAssignmentRepository facultyAssignmentRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AttemptFinalizer attemptFinalizer;

    public QuizService(
            QuizRepository quizRepository,
            QuizClassRepository quizClassRepository,
            QuizQuestionRepository quizQuestionRepository,
            QuizQuestionSnapshotRepository snapshotRepository,
            QuizAttemptRepository quizAttemptRepository,
            QuestionRepository questionRepository,
            QuestionOptionRepository optionRepository,
            FacultyAssignmentRepository facultyAssignmentRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            AttemptFinalizer attemptFinalizer) {
        this.quizRepository = quizRepository;
        this.quizClassRepository = quizClassRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.snapshotRepository = snapshotRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.facultyAssignmentRepository = facultyAssignmentRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.attemptFinalizer = attemptFinalizer;
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
        requireRequest(request);
        Quiz quiz = requireOwnedQuizRecord(quizId, facultyId);
        if (quiz.getStatus() == QuizStatus.CLOSED) {
            throw new ConflictException("CLOSED quizzes cannot be edited");
        }
        if (quiz.getStatus() == QuizStatus.SCHEDULED) {
            return updateScheduledQuiz(quiz, request, facultyId);
        }
        requireCurrentAssignments(quiz, facultyId);
        validateRequest(request);
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
    public QuizResponse publish(Long quizId) {
        Long facultyId = requireFacultyId();
        Quiz quiz = quizRepository.findByIdForUpdate(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));
        requireOwner(quiz, facultyId);
        if (quiz.getStatus() == QuizStatus.SCHEDULED) {
            requireCurrentAssignments(quiz, facultyId);
            return toResponse(quiz);
        }
        if (quiz.getStatus() != QuizStatus.DRAFT) {
            throw new ConflictException("Only DRAFT quizzes can be published");
        }

        Instant now = Instant.now();
        List<QuizQuestion> quizQuestions =
                quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(quizId);
        List<QuizClass> quizClasses = quizClassRepository.findByQuizIdOrderByIdAsc(quizId);
        List<Question> questions = validatePublishPreconditions(
                quiz, quizQuestions, quizClasses, facultyId, now);
        createSnapshots(quiz, quizQuestions, questions);
        quiz.setStatus(QuizStatus.SCHEDULED);
        quizRepository.save(quiz);
        eventPublisher.publishEvent(new QuizScheduledEvent(
                quiz.getId(),
                quizClasses.stream().map(quizClass -> quizClass.getSchoolClass().getId()).toList()));
        return toResponse(quiz);
    }

    @Transactional
    public QuizResponse manualClose(Long quizId) {
        Long facultyId = requireFacultyId();
        Quiz quiz = requireOwnedQuiz(quizId, facultyId);
        Instant now = Instant.now();
        if (!QuizStatusHelper.isOpenForAttempts(quiz, now)) {
            throw new ConflictException("Only a LIVE quiz can be manually closed");
        }

        quiz.setEndTime(now);
        quiz.setStatus(QuizStatus.CLOSED);
        quizRepository.save(quiz);
        quizAttemptRepository.findByQuizIdAndStatusOrderByIdAsc(quizId, AttemptStatus.IN_PROGRESS)
                .forEach(attemptFinalizer::submit);
        eventPublisher.publishEvent(new QuizClosedEvent(quizId));
        return toResponse(quiz);
    }

    @Transactional
    public QuizDuplicateResponse duplicate(Long quizId) {
        return duplicate(quizId, null);
    }

    @Transactional
    public QuizDuplicateResponse duplicate(Long quizId, String requestedTitle) {
        Long facultyId = requireFacultyId();
        Quiz source = requireOwnedQuizRecord(quizId, facultyId);
        String title = duplicateTitle(source, requestedTitle);

        List<FacultyAssignment> assignments = facultyAssignmentRepository.findByFacultyId(facultyId)
                .stream()
                .filter(assignment -> assignment.getSubject().getId().equals(source.getSubject().getId()))
                .toList();
        Set<Long> assignedClassIds = assignments.stream()
                .map(assignment -> assignment.getSchoolClass().getId())
                .collect(java.util.stream.Collectors.toSet());
        List<QuizClass> sourceClasses = quizClassRepository.findByQuizIdOrderByIdAsc(quizId);
        List<QuizClass> copiedClasses = sourceClasses.stream()
                .filter(quizClass -> assignedClassIds.contains(quizClass.getSchoolClass().getId()))
                .toList();
        List<QuizClassResponse> skippedClasses = sourceClasses.stream()
                .filter(quizClass -> !assignedClassIds.contains(quizClass.getSchoolClass().getId()))
                .map(quizClass -> new QuizClassResponse(
                        quizClass.getSchoolClass().getId(),
                        quizClass.getSchoolClass().getName(),
                        quizClass.getSchoolClass().getSection()))
                .toList();

        List<QuizQuestion> copiedQuestions = new ArrayList<>();
        List<SkippedQuizQuestionResponse> skippedQuestions = new ArrayList<>();
        for (QuizQuestion quizQuestion :
                quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(quizId)) {
            Question question = questionRepository.findById(quizQuestion.getQuestion().getId())
                    .orElse(null);
            if (question == null || !Boolean.TRUE.equals(question.getIsActive())) {
                skippedQuestions.add(new SkippedQuizQuestionResponse(
                        quizQuestion.getQuestion().getId(),
                        quizQuestion.getQuestion().getText()));
            } else {
                copiedQuestions.add(quizQuestion);
            }
        }

        Quiz copy = Quiz.builder()
                .title(title)
                .description(source.getDescription())
                .subject(source.getSubject())
                .createdBy(userRepository.getReferenceById(facultyId))
                .durationMinutes(source.getDurationMinutes())
                .startTime(null)
                .endTime(null)
                .maxAttempts(source.getMaxAttempts())
                .shuffleQuestions(source.getShuffleQuestions())
                .shuffleOptions(source.getShuffleOptions())
                .showAnswers(source.getShowAnswers())
                .passPercentage(source.getPassPercentage())
                .totalMarks(copiedQuestions.stream().mapToInt(QuizQuestion::getMarks).sum())
                .status(QuizStatus.DRAFT)
                .build();
        Quiz saved = quizRepository.save(copy);
        saveQuizClasses(saved, copiedClasses.stream().collect(java.util.stream.Collectors.toMap(
                quizClass -> quizClass.getSchoolClass().getId(),
                QuizClass::getSchoolClass,
                (first, ignored) -> first,
                LinkedHashMap::new)));
        for (int index = 0; index < copiedQuestions.size(); index++) {
            QuizQuestion original = copiedQuestions.get(index);
            quizQuestionRepository.save(QuizQuestion.builder()
                    .quiz(saved)
                    .question(original.getQuestion())
                    .sortOrder(index)
                    .marks(original.getMarks())
                    .build());
        }
        return new QuizDuplicateResponse(toResponse(saved), skippedQuestions, skippedClasses);
    }

    private QuizResponse updateScheduledQuiz(Quiz quiz, QuizRequest request, Long facultyId) {
        Instant now = Instant.now();
        boolean hasAttempts = quizAttemptRepository.existsByQuizId(quiz.getId());
        boolean locked = hasAttempts
                || (quiz.getStartTime() != null && !now.isBefore(quiz.getStartTime()));
        if (locked) {
            requireCurrentAssignments(quiz, facultyId);
            if (hasNonEndTimeChanges(quiz, request)) {
                throw new ConflictException(
                        "After a quiz has attempts or its start time has arrived, only end_time may be extended");
            }
            Instant requestedEnd = toInstant(request.endTime());
            if (requestedEnd == null || quiz.getEndTime() == null
                    || !requestedEnd.isAfter(quiz.getEndTime()) || !requestedEnd.isAfter(now)) {
                throw new ConflictException("end_time must be extended to a later instant after now");
            }
            quiz.setEndTime(requestedEnd);
            quizRepository.save(quiz);
            return toResponse(quiz);
        }

        validateRequest(request);
        List<FacultyAssignment> assignments = validatePublishAssignments(
                facultyId, request.subjectId(), request.classIds());
        Subject subject = assignments.getFirst().getSubject();
        Map<Long, SchoolClass> targetClasses = targetClasses(assignments, request.classIds());
        List<Question> selectedQuestions =
                validateQuestions(facultyId, subject, targetClasses, request);

        applyRequest(quiz, request, subject);
        quiz.setTotalMarks(totalMarks(request));
        quizRepository.save(quiz);
        quizClassRepository.deleteAllByQuizId(quiz.getId());
        quizQuestionRepository.deleteAllByQuizId(quiz.getId());
        saveQuizClasses(quiz, targetClasses);
        saveQuizQuestions(quiz, request.questions(), selectedQuestions);

        List<QuizQuestion> updatedQuestions =
                quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(quiz.getId());
        List<QuizClass> updatedClasses = quizClassRepository.findByQuizIdOrderByIdAsc(quiz.getId());
        List<Question> publishableQuestions =
                validatePublishPreconditions(quiz, updatedQuestions, updatedClasses, facultyId, now);
        if (quizAttemptRepository.existsByQuizId(quiz.getId())) {
            throw new ConflictException("Snapshots cannot be rebuilt after quiz attempts exist");
        }
        snapshotRepository.deleteAllByQuizId(quiz.getId());
        createSnapshots(quiz, updatedQuestions, publishableQuestions);
        return toResponse(quiz);
    }

    private List<Question> validatePublishPreconditions(
            Quiz quiz,
            List<QuizQuestion> quizQuestions,
            List<QuizClass> quizClasses,
            Long facultyId,
            Instant now) {
        if (quizQuestions.isEmpty()) {
            throw fieldError(quiz, "questions", "A quiz must have at least one question before publishing");
        }

        List<Question> questions = new ArrayList<>();
        for (QuizQuestion quizQuestion : quizQuestions) {
            Question question = questionRepository.findById(quizQuestion.getQuestion().getId())
                    .orElse(null);
            if (question == null || !Boolean.TRUE.equals(question.getIsActive())) {
                throw fieldError(quiz, "questions", "Every quiz question must still be active");
            }
            if (!quiz.getSubject().getId().equals(question.getSubject().getId())) {
                throw fieldError(quiz, "questions", "Every quiz question must belong to the quiz subject");
            }
            questions.add(question);
        }

        if (quizClasses.isEmpty()) {
            throw fieldError(quiz, "classIds", "A quiz must target at least one class before publishing");
        }
        boolean allAssigned = quizClasses.stream().allMatch(quizClass ->
                facultyAssignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(
                        facultyId, quizClass.getSchoolClass().getId(), quiz.getSubject().getId()));
        if (!allAssigned || !facultyAssignmentRepository.findByFacultyId(facultyId).stream()
                .anyMatch(assignment -> assignment.getSubject().getId().equals(quiz.getSubject().getId()))) {
            throw fieldError(quiz, "assignments",
                    "The quiz subject and every target class must be in your faculty assignments");
        }
        if (quiz.getDurationMinutes() == null || quiz.getDurationMinutes() <= 0) {
            throw fieldError(quiz, "durationMinutes", "Quiz duration must be greater than zero");
        }
        if (quiz.getStartTime() == null || !quiz.getStartTime().isAfter(now)) {
            throw fieldError(quiz, "startTime", "Quiz start_time must be in the future");
        }
        if (quiz.getEndTime() == null || !quiz.getEndTime().isAfter(quiz.getStartTime())) {
            throw fieldError(quiz, "endTime", "Quiz end_time must be after start_time");
        }
        return questions;
    }

    private List<FacultyAssignment> validatePublishAssignments(
            Long facultyId, Long subjectId, List<Long> classIds) {
        List<FacultyAssignment> assignments = facultyAssignmentRepository.findByFacultyId(facultyId);
        boolean allAssigned = classIds != null && !classIds.isEmpty()
                && classIds.stream().allMatch(classId -> assignments.stream().anyMatch(assignment ->
                        assignment.getSubject().getId().equals(subjectId)
                                && assignment.getSchoolClass().getId().equals(classId)));
        boolean subjectAssigned = assignments.stream()
                .anyMatch(assignment -> assignment.getSubject().getId().equals(subjectId));
        if (!allAssigned || !subjectAssigned) {
            throw fieldError(this, "assignments",
                    "The quiz subject and every target class must be in your faculty assignments");
        }
        return assignments.stream()
                .filter(assignment -> assignment.getSubject().getId().equals(subjectId))
                .toList();
    }

    private void createSnapshots(
            Quiz quiz, List<QuizQuestion> quizQuestions, List<Question> questions) {
        for (int index = 0; index < quizQuestions.size(); index++) {
            QuizQuestion quizQuestion = quizQuestions.get(index);
            Question question = questions.get(index);
            List<QuestionOption> options =
                    optionRepository.findByQuestionIdOrderBySortOrderAscIdAsc(question.getId());
            SnapshotOptions snapshotOptions = snapshotOptions(question.getType(), options);
            String json;
            try {
                json = objectMapper.writeValueAsString(snapshotOptions.options());
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Unable to serialize question snapshot options", exception);
            }
            snapshotRepository.insertIgnore(
                    quiz.getId(),
                    question.getId(),
                    question.getType().name(),
                    question.getText(),
                    json,
                    snapshotOptions.correctOptionKey(),
                    quizQuestion.getMarks(),
                    question.getModelAnswer(),
                    question.getExplanation(),
                    question.getImageUrl());
        }
    }

    private SnapshotOptions snapshotOptions(QuestionType type, List<QuestionOption> sourceOptions) {
        if (type == QuestionType.SHORT_ANSWER) {
            return new SnapshotOptions(List.of(), null);
        }
        List<QuestionOption> orderedOptions = new ArrayList<>(sourceOptions);
        if (type == QuestionType.TRUE_FALSE) {
            orderedOptions.sort((left, right) -> Boolean.compare(
                    isTrueOption(right), isTrueOption(left)));
        }
        List<Map<String, String>> options = new ArrayList<>();
        String correctKey = null;
        for (int index = 0; index < orderedOptions.size(); index++) {
            QuestionOption option = orderedOptions.get(index);
            String key = optionKey(index);
            String text = type == QuestionType.TRUE_FALSE
                    ? (isTrueOption(option) ? "True" : "False")
                    : option.getOptionText();
            Map<String, String> snapshotOption = new LinkedHashMap<>();
            snapshotOption.put("key", key);
            snapshotOption.put("text", text);
            options.add(snapshotOption);
            if (Boolean.TRUE.equals(option.getIsCorrect())) {
                correctKey = key;
            }
        }
        return new SnapshotOptions(List.copyOf(options), correctKey);
    }

    private boolean isTrueOption(QuestionOption option) {
        return "true".equalsIgnoreCase(option.getOptionText().trim());
    }

    private String optionKey(int index) {
        StringBuilder key = new StringBuilder();
        int value = index;
        do {
            key.insert(0, (char) ('A' + value % 26));
            value = value / 26 - 1;
        } while (value >= 0);
        return key.toString();
    }

    private boolean hasNonEndTimeChanges(Quiz quiz, QuizRequest request) {
        if (!java.util.Objects.equals(quiz.getTitle(), request.title() == null ? null : request.title().trim())
                || !java.util.Objects.equals(quiz.getDescription(), normalize(request.description()))
                || !java.util.Objects.equals(quiz.getSubject().getId(), request.subjectId())
                || !java.util.Objects.equals(quiz.getDurationMinutes(), request.durationMinutes())
                || !java.util.Objects.equals(quiz.getMaxAttempts(), request.maxAttempts())
                || !java.util.Objects.equals(quiz.getShuffleQuestions(), request.shuffleQuestions())
                || !java.util.Objects.equals(quiz.getShuffleOptions(), request.shuffleOptions())
                || quiz.getShowAnswers() != request.showAnswers()
                || !java.util.Objects.equals(quiz.getPassPercentage(), request.passPercentage())
                || !java.util.Objects.equals(quiz.getStartTime(), toInstant(request.startTime()))) {
            return true;
        }
        List<Long> oldClassIds = quizClassRepository.findByQuizIdOrderByIdAsc(quiz.getId())
                .stream().map(row -> row.getSchoolClass().getId()).sorted().toList();
        List<Long> newClassIds = request.classIds() == null
                ? List.of() : request.classIds().stream().sorted().toList();
        if (!oldClassIds.equals(newClassIds)) {
            return true;
        }
        List<QuizQuestion> oldQuestions =
                quizQuestionRepository.findByQuizIdOrderBySortOrderAscIdAsc(quiz.getId());
        List<QuizQuestionRequest> newQuestions =
                request.questions() == null ? List.of() : request.questions();
        if (oldQuestions.size() != newQuestions.size()) {
            return true;
        }
        for (int index = 0; index < oldQuestions.size(); index++) {
            if (!oldQuestions.get(index).getQuestion().getId().equals(newQuestions.get(index).questionId())
                    || !oldQuestions.get(index).getMarks().equals(newQuestions.get(index).marks())) {
                return true;
            }
        }
        return false;
    }

    private void applyRequest(Quiz quiz, QuizRequest request, Subject subject) {
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
    }

    private String duplicateTitle(Quiz source, String requestedTitle) {
        if (requestedTitle != null && !requestedTitle.isBlank()) {
            String title = requestedTitle.trim();
            if (title.length() > 200) {
                throw new IllegalArgumentException("Quiz title must not exceed 200 characters");
            }
            return title;
        }
        String prefix = "Copy of ";
        String sourceTitle = source.getTitle() == null ? "" : source.getTitle();
        return prefix + sourceTitle.substring(0, Math.min(sourceTitle.length(), 200 - prefix.length()));
    }

    private void requireRequest(QuizRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Quiz request is required");
        }
    }

    private record SnapshotOptions(List<Map<String, String>> options, String correctOptionKey) {
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
        Quiz quiz = requireOwnedQuizRecord(quizId, facultyId);
        requireCurrentAssignments(quiz, facultyId);
        return quiz;
    }

    private Quiz requireOwnedQuizRecord(Long quizId, Long facultyId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));
        requireOwner(quiz, facultyId);
        return quiz;
    }

    private void requireOwner(Quiz quiz, Long facultyId) {
        if (!facultyId.equals(quiz.getCreatedBy().getId())) {
            throw new AccessDeniedException("Faculty may only access their own quizzes");
        }
    }

    private void requireCurrentAssignments(Quiz quiz, Long facultyId) {
        List<QuizClass> classes = quizClassRepository.findByQuizIdOrderByIdAsc(quiz.getId());
        if (classes.isEmpty() || classes.stream().anyMatch(quizClass ->
                !facultyAssignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(
                        facultyId, quizClass.getSchoolClass().getId(), quiz.getSubject().getId()))) {
            throw new AccessDeniedException("Faculty is no longer assigned to this quiz");
        }
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
                throw fieldError(request, "questions", "Every quiz question must still be active");
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
