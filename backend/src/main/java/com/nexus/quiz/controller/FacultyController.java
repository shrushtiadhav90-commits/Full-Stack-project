package com.nexus.quiz.controller;

import com.nexus.quiz.dto.ApiResponse;
import com.nexus.quiz.dto.admin.PageResponse;
import com.nexus.quiz.dto.faculty.*;
import com.nexus.quiz.entity.QuestionDifficulty;
import com.nexus.quiz.entity.QuestionType;
import com.nexus.quiz.entity.Quiz;
import com.nexus.quiz.service.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/faculty")
@PreAuthorize("hasRole('FACULTY')")
public class FacultyController {

    private final QuestionService questionService;
    private final QuizService quizService;
    private final GradingService gradingService;
    private final FacultyDashboardService dashboardService;
    private final QuizReportService reportService;

    public FacultyController(
            QuestionService questionService,
            QuizService quizService,
            GradingService gradingService,
            FacultyDashboardService dashboardService,
            QuizReportService reportService) {
        this.questionService = questionService;
        this.quizService = quizService;
        this.gradingService = gradingService;
        this.dashboardService = dashboardService;
        this.reportService = reportService;
    }

    @GetMapping("/assignments")
    public ResponseEntity<ApiResponse<PageResponse<FacultyAssignmentOptionResponse>>> assignments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "className,asc") String sort) {
        List<FacultyAssignmentOptionResponse> assignments = quizService.listMyAssignments();
        Comparator<FacultyAssignmentOptionResponse> comparator = assignmentComparator(sort);
        List<FacultyAssignmentOptionResponse> ordered = assignments.stream().sorted(comparator).toList();
        return ResponseEntity.ok(ApiResponse.success("Faculty assignments", pageOf(ordered, page, size)));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<FacultyDashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success("Faculty dashboard", dashboardService.getDashboard()));
    }

    @PostMapping("/questions")
    public ResponseEntity<ApiResponse<QuestionResponse>> createQuestion(
            @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                "Question created", questionService.createQuestion(request, null)));
    }

    @GetMapping("/questions")
    public ResponseEntity<ApiResponse<PageResponse<QuestionResponse>>> listQuestions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long subject,
            @RequestParam(required = false) String classLevel,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) QuestionDifficulty difficulty,
            @RequestParam(required = false) QuestionType type) {
        return ResponseEntity.ok(ApiResponse.success("Questions",
                questionService.searchQuestions(page, size, sort, keyword,
                        subject, classLevel, topic, difficulty, type)));
    }

    @GetMapping("/questions/{id}")
    public ResponseEntity<ApiResponse<QuestionResponse>> getQuestion(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Question", questionService.getQuestion(id)));
    }

    @PutMapping("/questions/{id}")
    public ResponseEntity<ApiResponse<QuestionResponse>> updateQuestion(
            @PathVariable Long id, @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Question updated", questionService.updateQuestion(id, request, null)));
    }

    @DeleteMapping("/questions/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(@PathVariable Long id) {
        questionService.deleteQuestion(id);
        return ResponseEntity.ok(ApiResponse.success("Question deactivated", null));
    }

    @PostMapping(value = "/questions/{id}/image", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<QuestionResponse>> uploadQuestionImage(
            @PathVariable Long id, @RequestPart("image") MultipartFile image) {
        return ResponseEntity.ok(ApiResponse.success(
                "Question image uploaded", questionService.uploadQuestionImage(id, image)));
    }

    @DeleteMapping("/questions/{id}/image")
    public ResponseEntity<ApiResponse<QuestionResponse>> deleteQuestionImage(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Question image removed", questionService.deleteQuestionImage(id)));
    }

    @PostMapping("/quizzes")
    public ResponseEntity<ApiResponse<QuizResponse>> createQuiz(@Valid @RequestBody QuizRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success("Quiz created", quizService.createQuiz(request)));
    }

    @GetMapping("/quizzes")
    public ResponseEntity<ApiResponse<PageResponse<QuizResponse>>> listQuizzes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) Quiz.EffectiveStatus status,
            @RequestParam(required = false) Long subject,
            @RequestParam(name = "class", required = false) Long classId,
            @RequestParam(name = "classId", required = false) Long classIdAlias) {
        if (classId != null && classIdAlias != null && !classId.equals(classIdAlias)) {
            throw new IllegalArgumentException("class and classId filters must match when both are provided");
        }
        return ResponseEntity.ok(ApiResponse.success("Quizzes",
                quizService.listMyQuizzes(page, size, sort, status, subject,
                        classId == null ? classIdAlias : classId)));
    }

    @GetMapping("/quizzes/{id}")
    public ResponseEntity<ApiResponse<QuizResponse>> getQuiz(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Quiz", quizService.getQuiz(id)));
    }

    @PutMapping("/quizzes/{id}")
    public ResponseEntity<ApiResponse<QuizResponse>> updateQuiz(
            @PathVariable Long id, @Valid @RequestBody QuizRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Quiz updated", quizService.updateQuiz(id, request)));
    }

    @DeleteMapping("/quizzes/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteQuiz(@PathVariable Long id) {
        quizService.deleteQuiz(id);
        return ResponseEntity.ok(ApiResponse.success("Quiz deleted", null));
    }

    @PostMapping("/quizzes/{id}/publish")
    public ResponseEntity<ApiResponse<QuizResponse>> publishQuiz(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Quiz published", quizService.publish(id)));
    }

    @PostMapping("/quizzes/{id}/close")
    public ResponseEntity<ApiResponse<QuizResponse>> closeQuiz(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Quiz closed", quizService.manualClose(id)));
    }

    @PostMapping("/quizzes/{id}/duplicate")
    public ResponseEntity<ApiResponse<QuizDuplicateResponse>> duplicateQuiz(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) DuplicateQuizRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                "Quiz duplicated", quizService.duplicate(id, request == null ? null : request.title())));
    }

    @GetMapping("/grading")
    public ResponseEntity<ApiResponse<PageResponse<GradingQueueItemResponse>>> gradingQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "submittedAt,asc") String sort,
            @RequestParam(required = false) Long quizId,
            @RequestParam(required = false) Long classId) {
        validateQueueSort(sort);
        return ResponseEntity.ok(ApiResponse.success("Grading queue",
                gradingService.getGradingQueue(page, size, quizId, classId)));
    }

    @PutMapping("/grading/answers/{answerId}")
    public ResponseEntity<ApiResponse<GradeAnswerResponse>> gradeAnswer(
            @PathVariable Long answerId, @Valid @RequestBody GradeAnswerRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Answer graded", gradingService.gradeAnswer(
                answerId, request.marksAwarded(), request.feedback())));
    }

    @GetMapping("/quizzes/{id}/report")
    public ResponseEntity<ApiResponse<QuizReportResponse>> quizReport(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Quiz report", reportService.getQuizReport(id)));
    }

    private PageResponse<FacultyAssignmentOptionResponse> pageOf(
            List<FacultyAssignmentOptionResponse> items, int page, int size) {
        validatePage(page, size);
        int start = (int) Math.min((long) page * size, items.size());
        int end = Math.min(start + size, items.size());
        return new PageResponse<>(items.subList(start, end), page, size, items.size(),
                (items.size() + size - 1) / size);
    }

    private Comparator<FacultyAssignmentOptionResponse> assignmentComparator(String sort) {
        String[] parts = sort == null ? new String[0] : sort.split(",", 2);
        if (parts.length == 0 || !List.of("className", "subjectName").contains(parts[0].trim())
                || (parts.length > 1 && !List.of("asc", "desc").contains(parts[1].trim().toLowerCase()))) {
            throw new IllegalArgumentException("Unsupported assignment sort field or direction");
        }
        Comparator<FacultyAssignmentOptionResponse> comparator = parts[0].trim().equals("subjectName")
                ? Comparator.comparing(FacultyAssignmentOptionResponse::subjectName,
                        String.CASE_INSENSITIVE_ORDER)
                : Comparator.comparing(FacultyAssignmentOptionResponse::className,
                        String.CASE_INSENSITIVE_ORDER);
        return parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc")
                ? comparator.reversed() : comparator;
    }

    private void validateQueueSort(String sort) {
        if (sort == null || !List.of("submittedAt,asc").contains(sort)) {
            throw new IllegalArgumentException("Grading queue must be sorted by submittedAt");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100");
        }
    }
}
