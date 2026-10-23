package com.nexus.quiz.service;

import com.nexus.quiz.dto.faculty.FacultyDashboardResponse;
import com.nexus.quiz.dto.faculty.QuizResponse;
import com.nexus.quiz.entity.Quiz;
import com.nexus.quiz.repository.QuizRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class FacultyDashboardService {

    private final QuizService quizService;
    private final QuizRepository quizRepository;
    private final FacultyIdentity facultyIdentity;
    private final GradingService gradingService;

    public FacultyDashboardService(
            QuizService quizService,
            QuizRepository quizRepository,
            FacultyIdentity facultyIdentity,
            GradingService gradingService) {
        this.quizService = quizService;
        this.quizRepository = quizRepository;
        this.facultyIdentity = facultyIdentity;
        this.gradingService = gradingService;
    }

    @Transactional(readOnly = true)
    public FacultyDashboardResponse getDashboard() {
        Long facultyId = facultyIdentity.requireFacultyId();
        List<com.nexus.quiz.dto.faculty.FacultyAssignmentOptionResponse> assignments =
                quizService.listMyAssignments();
        Instant now = Instant.now();
        List<Quiz> quizzes = quizRepository.findByCreatedByIdOrderByCreatedAtDescIdDesc(facultyId)
                .stream()
                .filter(quiz -> quizService.hasCurrentAssignmentsForFaculty(quiz, facultyId))
                .toList();
        EnumMap<Quiz.EffectiveStatus, Long> counts = new EnumMap<>(Quiz.EffectiveStatus.class);
        for (Quiz.EffectiveStatus status : Quiz.EffectiveStatus.values()) {
            counts.put(status, quizzes.stream()
                    .filter(quiz -> quiz.effectiveStatusAt(now) == status).count());
        }
        List<QuizResponse> upcoming = quizzes.stream()
                .filter(quiz -> quiz.effectiveStatusAt(now) == Quiz.EffectiveStatus.SCHEDULED)
                .sorted(Comparator.comparing(Quiz::getStartTime))
                .limit(5)
                .map(quizService::toFacultyResponse)
                .toList();
        List<QuizResponse> live = quizzes.stream()
                .filter(quiz -> quiz.effectiveStatusAt(now) == Quiz.EffectiveStatus.LIVE)
                .sorted(Comparator.comparing(Quiz::getEndTime))
                .limit(5)
                .map(quizService::toFacultyResponse)
                .toList();
        return new FacultyDashboardResponse(
                assignments, upcoming, live, gradingService.countPendingShortAnswers(), Map.copyOf(counts));
    }
}
