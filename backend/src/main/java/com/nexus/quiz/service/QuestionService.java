package com.nexus.quiz.service;

import com.nexus.quiz.dto.admin.PageResponse;
import com.nexus.quiz.dto.faculty.QuestionOptionRequest;
import com.nexus.quiz.dto.faculty.QuestionOptionResponse;
import com.nexus.quiz.dto.faculty.QuestionRequest;
import com.nexus.quiz.dto.faculty.QuestionResponse;
import com.nexus.quiz.entity.*;
import com.nexus.quiz.exception.ResourceNotFoundException;
import com.nexus.quiz.exception.ServiceValidationException;
import com.nexus.quiz.repository.*;
import org.apache.tika.Tika;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class QuestionService {

    private static final String IMAGE_FOLDER = "question-images";
    private static final String IMAGE_URL_PREFIX = "/api/v1/files/question-images/";
    private static final long MAX_IMAGE_SIZE = 2L * 1024 * 1024;
    private static final Map<String, String> IMAGE_SUFFIXES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");
    private static final Set<String> SORT_FIELDS =
            Set.of("id", "text", "createdAt", "updatedAt", "difficulty", "type", "marks");

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final QuizQuestionSnapshotRepository snapshotRepository;
    private final FacultyAssignmentRepository facultyAssignmentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final Tika tika = new Tika();

    public QuestionService(
            QuestionRepository questionRepository,
            QuestionOptionRepository optionRepository,
            QuizQuestionSnapshotRepository snapshotRepository,
            FacultyAssignmentRepository facultyAssignmentRepository,
            UserRepository userRepository,
            FileStorageService fileStorageService) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.snapshotRepository = snapshotRepository;
        this.facultyAssignmentRepository = facultyAssignmentRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public QuestionResponse createQuestion(QuestionRequest request, MultipartFile image) {
        Long facultyId = requireFacultyId();
        validateQuestion(request);
        List<FacultyAssignment> assignments = assignmentsForSubject(facultyId, request.subjectId());
        Subject subject = assignments.stream()
                .map(FacultyAssignment::getSubject)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Faculty is not assigned to this subject"));
        requireClassLevelAssignment(assignments, request.classLevel());

        Question question = Question.builder()
                .createdBy(userRepository.getReferenceById(facultyId))
                .subject(subject)
                .classLevel(request.classLevel().trim())
                .topic(normalize(request.topic()))
                .type(request.type())
                .text(request.text().trim())
                .difficulty(request.difficulty())
                .marks(request.marks())
                .explanation(request.explanation())
                .modelAnswer(request.modelAnswer())
                .isActive(true)
                .build();

        String imageUrl = image != null && !image.isEmpty() ? storeImage(image) : null;
        question.setImageUrl(imageUrl);
        try {
            Question saved = questionRepository.save(question);
            saveOptions(saved, request.options());
            return toResponse(saved);
        } catch (RuntimeException exception) {
            if (imageUrl != null) {
                deleteStoredImage(imageUrl);
            }
            throw exception;
        }
    }

    @Transactional
    public QuestionResponse updateQuestion(Long questionId, QuestionRequest request, MultipartFile image) {
        Long facultyId = requireFacultyId();
        validateQuestion(request);
        Question question = requireOwnedActiveQuestion(questionId, facultyId);
        if (request.type() != question.getType()) {
            throw fieldError(request, "type", "Question type cannot be changed");
        }

        List<FacultyAssignment> assignments = assignmentsForSubject(facultyId, request.subjectId());
        Subject subject = assignments.stream()
                .map(FacultyAssignment::getSubject)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Faculty is not assigned to this subject"));
        requireClassLevelAssignment(assignments, request.classLevel());

        String previousImageUrl = question.getImageUrl();
        question.setSubject(subject);
        question.setClassLevel(request.classLevel().trim());
        question.setTopic(normalize(request.topic()));
        question.setText(request.text().trim());
        question.setDifficulty(request.difficulty());
        question.setMarks(request.marks());
        question.setExplanation(request.explanation());
        question.setModelAnswer(request.modelAnswer());

        String replacementImageUrl = null;
        boolean hasReplacement = image != null && !image.isEmpty();
        if (hasReplacement) {
            replacementImageUrl = storeImage(image);
            question.setImageUrl(replacementImageUrl);
        } else if (request.removeImage()) {
            question.setImageUrl(null);
        }

        try {
            Question saved = questionRepository.save(question);
            optionRepository.deleteAllByQuestionId(questionId);
            saveOptions(saved, request.options());
            if ((hasReplacement || request.removeImage())
                    && previousImageUrl != null
                    && !previousImageUrl.equals(saved.getImageUrl())) {
                deleteImageUnlessSnapshotted(previousImageUrl);
            }
            return toResponse(saved);
        } catch (RuntimeException exception) {
            if (replacementImageUrl != null) {
                deleteStoredImage(replacementImageUrl);
            }
            throw exception;
        }
    }

    @Transactional
    public void deleteQuestion(Long questionId) {
        Long facultyId = requireFacultyId();
        Question question = requireOwnedActiveQuestion(questionId, facultyId);
        question.setIsActive(false);
        questionRepository.save(question);
    }

    @Transactional
    public QuestionResponse deleteQuestionImage(Long questionId) {
        Long facultyId = requireFacultyId();
        Question question = requireOwnedActiveQuestion(questionId, facultyId);
        String previousImageUrl = question.getImageUrl();
        if (previousImageUrl != null) {
            question.setImageUrl(null);
            questionRepository.save(question);
            deleteImageUnlessSnapshotted(previousImageUrl);
        }
        return toResponse(question);
    }

    @Transactional
    public QuestionResponse uploadQuestionImage(Long questionId, MultipartFile image) {
        Long facultyId = requireFacultyId();
        if (image == null || image.isEmpty()) {
            throw new ServiceValidationException("image", "A question image is required");
        }
        Question question = requireOwnedActiveQuestion(questionId, facultyId);
        String previousImageUrl = question.getImageUrl();
        String replacementImageUrl = storeImage(image);
        question.setImageUrl(replacementImageUrl);
        try {
            Question saved = questionRepository.save(question);
            if (previousImageUrl != null && !previousImageUrl.equals(replacementImageUrl)) {
                deleteImageUnlessSnapshotted(previousImageUrl);
            }
            return toResponse(saved);
        } catch (RuntimeException exception) {
            deleteStoredImage(replacementImageUrl);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public QuestionResponse getQuestion(Long questionId) {
        Long facultyId = requireFacultyId();
        return toResponse(requireOwnedActiveQuestion(questionId, facultyId));
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionResponse> searchQuestions(
            int page,
            int size,
            String sort,
            String keyword,
            Long subjectId,
            String classLevel,
            String topic,
            QuestionDifficulty difficulty,
            QuestionType type) {
        Long facultyId = requireFacultyId();
        Pageable pageable = pageable(page, size, sort);
        var specification = org.springframework.data.jpa.domain.Specification
                .<Question>where((root, query, builder) -> builder.and(
                        builder.equal(root.get("createdBy").get("id"), facultyId),
                        builder.isTrue(root.get("isActive"))));
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) ->
                    builder.like(builder.lower(root.get("text")), pattern));
        }
        if (subjectId != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("subject").get("id"), subjectId));
        }
        if (classLevel != null && !classLevel.isBlank()) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(builder.lower(root.get("classLevel")),
                            classLevel.trim().toLowerCase(Locale.ROOT)));
        }
        if (topic != null && !topic.isBlank()) {
            String pattern = "%" + topic.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) ->
                    builder.like(builder.lower(root.get("topic")), pattern));
        }
        if (difficulty != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("difficulty"), difficulty));
        }
        if (type != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("type"), type));
        }
        List<FacultyAssignment> assignments = facultyAssignmentRepository.findByFacultyId(facultyId);
        if (assignments.isEmpty()) {
            return PageResponse.from(Page.empty(pageable));
        }
        specification = specification.and((root, query, builder) -> builder.or(
                assignments.stream().map(assignment -> builder.and(
                        builder.equal(root.get("subject").get("id"),
                                assignment.getSubject().getId()),
                        builder.equal(builder.lower(root.get("classLevel")),
                                assignment.getSchoolClass().getName().toLowerCase(Locale.ROOT))))
                        .toArray(jakarta.persistence.criteria.Predicate[]::new)));
        Page<QuestionResponse> responsePage = questionRepository.findAll(specification, pageable)
                .map(this::toResponse);
        return PageResponse.from(responsePage);
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

    private Question requireOwnedActiveQuestion(Long questionId, Long facultyId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));
        if (!facultyId.equals(question.getCreatedBy().getId())) {
            throw new AccessDeniedException("Faculty may only access their own questions");
        }
        if (!Boolean.TRUE.equals(question.getIsActive())) {
            throw new ResourceNotFoundException("Question not found");
        }
        List<FacultyAssignment> assignments = assignmentsForSubject(facultyId,
                question.getSubject().getId());
        requireClassLevelAssignment(assignments, question.getClassLevel());
        return question;
    }

    private List<FacultyAssignment> assignmentsForSubject(Long facultyId, Long subjectId) {
        List<FacultyAssignment> assignments = facultyAssignmentRepository.findByFacultyId(facultyId)
                .stream()
                .filter(assignment -> assignment.getSubject().getId().equals(subjectId))
                .toList();
        if (assignments.isEmpty()) {
            throw new AccessDeniedException("Faculty is not assigned to this subject");
        }
        return assignments;
    }

    private void requireClassLevelAssignment(List<FacultyAssignment> assignments, String classLevel) {
        boolean assignedClassLevel = assignments.stream()
                .map(FacultyAssignment::getSchoolClass)
                .map(SchoolClass::getName)
                .anyMatch(name -> name.equalsIgnoreCase(classLevel.trim()));
        if (!assignedClassLevel) {
            throw new AccessDeniedException("Faculty is not assigned to this class level");
        }
    }

    private void validateQuestion(QuestionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Question request is required");
        }
        if (request.text() == null || request.text().isBlank()) {
            throw fieldError(request, "text", "Question text is required");
        }
        if (request.type() == null) {
            throw fieldError(request, "type", "Question type is required");
        }
        if (request.subjectId() == null) {
            throw fieldError(request, "subjectId", "Subject is required");
        }
        if (request.classLevel() == null || request.classLevel().isBlank()) {
            throw fieldError(request, "classLevel", "Class level is required");
        }
        if (request.difficulty() == null) {
            throw fieldError(request, "difficulty", "Difficulty is required");
        }
        if (request.marks() == null || request.marks() <= 0) {
            throw fieldError(request, "marks", "Marks must be positive");
        }
        List<QuestionOptionRequest> options = request.options() == null ? List.of() : request.options();
        if (options.stream().anyMatch(option -> option.optionText() == null
                || option.optionText().isBlank())) {
            throw fieldError(request, "options", "Option text is required");
        }
        long correctCount = options.stream().filter(QuestionOptionRequest::isCorrect).count();
        boolean validOptions = switch (request.type()) {
            case MCQ -> options.size() >= 2 && options.size() <= 6 && correctCount == 1;
            case TRUE_FALSE -> options.size() == 2 && correctCount == 1
                    && options.stream().allMatch(option -> option.optionText() != null)
                    && options.stream().map(option -> option.optionText().trim().toLowerCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toSet()).equals(Set.of("true", "false"));
            case SHORT_ANSWER -> options.isEmpty();
        };
        if (!validOptions) {
            throw fieldError(request, "options", "Options must match the selected question type");
        }
        if (request.topic() != null && request.topic().length() > 100) {
            throw fieldError(request, "topic", "Topic must not exceed 100 characters");
        }
        if (request.classLevel().length() > 20) {
            throw fieldError(request, "classLevel", "Class level must not exceed 20 characters");
        }
        List<String> keys = options.stream().map(QuestionOptionRequest::optionKey)
                .filter(key -> key != null && !key.isBlank()).toList();
        if (keys.size() != keys.stream().distinct().count()) {
            throw fieldError(request, "options", "Option keys must be unique");
        }
        Set<String> effectiveKeys = new HashSet<>();
        for (int index = 0; index < options.size(); index++) {
            String providedKey = options.get(index).optionKey();
            String key = providedKey == null || providedKey.isBlank()
                    ? String.valueOf((char) ('A' + index))
                    : providedKey.trim();
            if (key.length() > 10 || !effectiveKeys.add(key)) {
                throw fieldError(request, "options", "Option keys must be unique and at most 10 characters");
            }
        }
    }

    private ServiceValidationException fieldError(Object target, String field, String message) {
        return new ServiceValidationException(field, message);
    }

    private void saveOptions(Question question, List<QuestionOptionRequest> requests) {
        List<QuestionOptionRequest> options = requests == null ? List.of() : requests;
        List<QuestionOption> entities = new ArrayList<>();
        for (int index = 0; index < options.size(); index++) {
            QuestionOptionRequest request = options.get(index);
            String key = request.optionKey() == null || request.optionKey().isBlank()
                    ? String.valueOf((char) ('A' + index))
                    : request.optionKey().trim();
            if (key.length() > 10 || entities.stream()
                    .anyMatch(option -> option.getOptionKey().equals(key))) {
                throw fieldError(request, "options", "Option keys must be unique and at most 10 characters");
            }
            entities.add(QuestionOption.builder()
                    .question(question)
                    .optionKey(key)
                    .optionText(request.optionText().trim())
                    .isCorrect(request.isCorrect())
                    .sortOrder(request.sortOrder() == null ? index : request.sortOrder())
                    .build());
        }
        optionRepository.saveAll(entities);
    }

    private String storeImage(MultipartFile image) {
        validateImage(image);
        try {
            return IMAGE_URL_PREFIX + fileStorageService.store(image, IMAGE_FOLDER);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store question image", exception);
        }
    }

    private void validateImage(MultipartFile image) {
        if (image.getSize() > MAX_IMAGE_SIZE) {
            throw fieldError(image, "image", "Question images must not exceed 2 MB");
        }
        try (InputStream input = image.getInputStream()) {
            if (!IMAGE_SUFFIXES.containsKey(tika.detect(input))) {
                throw fieldError(image, "image", "Question image must be JPEG, PNG, or WebP");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect question image", exception);
        }
    }

    private void deleteImageUnlessSnapshotted(String imageUrl) {
        if (snapshotRepository.existsByImageUrl(imageUrl)) {
            return;
        }
        deleteStoredImage(imageUrl);
    }

    private void deleteStoredImage(String imageUrl) {
        String filename = imageUrl.substring(IMAGE_URL_PREFIX.length());
        try {
            fileStorageService.delete(IMAGE_FOLDER, filename);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to delete question image", exception);
        }
    }

    private Pageable pageable(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100");
        }
        String[] parts = sort == null || sort.isBlank()
                ? new String[]{"createdAt", "desc"}
                : sort.split(",", 2);
        String property = parts[0].trim();
        if (!SORT_FIELDS.contains(property)) {
            throw new IllegalArgumentException("Unsupported question sort field");
        }
        Sort.Direction direction = parts.length == 1 || "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : null;
        if (direction == null) {
            throw new IllegalArgumentException("Sort direction must be asc or desc");
        }
        return PageRequest.of(page, size, Sort.by(direction, property));
    }

    private QuestionResponse toResponse(Question question) {
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
        return new QuestionResponse(
                question.getId(),
                question.getCreatedBy().getId(),
                question.getSubject().getId(),
                question.getSubject().getName(),
                question.getClassLevel(),
                question.getTopic(),
                question.getType(),
                question.getText(),
                question.getImageUrl(),
                question.getDifficulty(),
                question.getMarks(),
                question.getExplanation(),
                question.getModelAnswer(),
                Boolean.TRUE.equals(question.getIsActive()),
                question.getCreatedAt(),
                question.getUpdatedAt(),
                options);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
