package com.nexus.quiz.service;

import com.nexus.quiz.dto.faculty.QuizReportResponse;
import com.nexus.quiz.dto.faculty.QuizReportStudentResponse;
import com.nexus.quiz.dto.faculty.QuizScoreBucketResponse;
import com.nexus.quiz.entity.*;
import com.nexus.quiz.exception.ResourceNotFoundException;
import com.nexus.quiz.repository.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuizReportService {

    private final QuizRepository quizRepository;
    private final QuizClassRepository quizClassRepository;
    private final QuizAttemptRepository attemptRepository;
    private final StudentRepository studentRepository;
    private final FacultyAssignmentRepository assignmentRepository;
    private final FacultyIdentity facultyIdentity;

    public QuizReportService(
            QuizRepository quizRepository,
            QuizClassRepository quizClassRepository,
            QuizAttemptRepository attemptRepository,
            StudentRepository studentRepository,
            FacultyAssignmentRepository assignmentRepository,
            FacultyIdentity facultyIdentity) {
        this.quizRepository = quizRepository;
        this.quizClassRepository = quizClassRepository;
        this.attemptRepository = attemptRepository;
        this.studentRepository = studentRepository;
        this.assignmentRepository = assignmentRepository;
        this.facultyIdentity = facultyIdentity;
    }

    @Transactional(readOnly = true)
    public QuizReportResponse getQuizReport(Long quizId) {
        Long facultyId = facultyIdentity.requireFacultyId();
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found"));
        if (!facultyId.equals(quiz.getCreatedBy().getId())) {
            throw new AccessDeniedException("Faculty may only report on their own quizzes");
        }
        List<QuizClass> quizClasses = quizClassRepository.findByQuizIdOrderByIdAsc(quizId);
        List<Long> classIds = quizClasses.stream()
                .filter(quizClass -> assignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(
                        facultyId, quizClass.getSchoolClass().getId(), quiz.getSubject().getId()))
                .map(quizClass -> quizClass.getSchoolClass().getId())
                .distinct()
                .toList();
        if (classIds.size() != quizClasses.size() || classIds.isEmpty()) {
            throw new AccessDeniedException("Faculty is no longer assigned to this quiz");
        }

        List<Student> students = studentRepository.findBySchoolClassIdInOrderByUserFullNameAsc(classIds);
        Map<Long, List<QuizAttempt>> attemptsByStudent = new HashMap<>();
        for (QuizAttempt attempt : attemptRepository.findByQuizIdOrderByIdAsc(quizId)) {
            attemptsByStudent.computeIfAbsent(attempt.getStudent().getId(), ignored -> new ArrayList<>())
                    .add(attempt);
        }

        List<QuizReportStudentResponse> table = new ArrayList<>();
        List<QuizReportStudentResponse> notAttempted = new ArrayList<>();
        List<BigDecimal> bestScores = new ArrayList<>();
        long passCount = 0;
        List<QuizReportStudentResponse> gradedStudents = new ArrayList<>();
        for (Student student : students) {
            List<QuizAttempt> studentAttempts =
                    attemptsByStudent.getOrDefault(student.getId(), List.of());
            List<QuizAttempt> gradedAttempts = studentAttempts.stream()
                    .filter(attempt -> attempt.getStatus() == AttemptStatus.GRADED
                            && attempt.getPercentage() != null)
                    .sorted(Comparator.comparing(QuizAttempt::getPercentage).reversed()
                            .thenComparing(QuizAttempt::getId))
                    .toList();
            QuizAttempt best = gradedAttempts.isEmpty() ? null : gradedAttempts.getFirst();
            String status = studentAttempts.isEmpty() ? "NOT_ATTEMPTED"
                    : studentAttempts.stream().anyMatch(attempt -> attempt.getStatus() == AttemptStatus.IN_PROGRESS)
                    ? "IN_PROGRESS"
                    : studentAttempts.stream().anyMatch(attempt -> attempt.getStatus() == AttemptStatus.SUBMITTED)
                    ? "PENDING" : "GRADED";
            QuizReportStudentResponse response = new QuizReportStudentResponse(
                    student.getId(),
                    student.getUser().getFullName(),
                    best == null ? null : best.getPercentage(),
                    studentAttempts.size(),
                    status);
            table.add(response);
            if (studentAttempts.isEmpty()) {
                notAttempted.add(response);
            }
            if (best != null) {
                bestScores.add(best.getPercentage());
                gradedStudents.add(response);
                if (best.getPercentage().compareTo(quiz.getPassPercentage()) >= 0) {
                    passCount++;
                }
            }
        }

        BigDecimal average = bestScores.isEmpty() ? BigDecimal.ZERO
                : bestScores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(bestScores.size()), 2, RoundingMode.HALF_UP);
        BigDecimal highest = bestScores.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal lowest = bestScores.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal passPercentage = gradedStudents.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(passCount * 100)
                        .divide(BigDecimal.valueOf(gradedStudents.size()), 2, RoundingMode.HALF_UP);
        long[] bucketCounts = new long[10];
        for (BigDecimal score : bestScores) {
            int bucket = Math.min(9, Math.max(0, score.divideToIntegralValue(BigDecimal.TEN).intValue()));
            bucketCounts[bucket]++;
        }
        List<QuizScoreBucketResponse> buckets = new ArrayList<>();
        for (int index = 0; index < bucketCounts.length; index++) {
            buckets.add(new QuizScoreBucketResponse(
                    index == 9 ? "90-100" : (index * 10) + "-" + (index * 10 + 9),
                    bucketCounts[index]));
        }
        long attemptedCount = students.size() - notAttempted.size();
        return new QuizReportResponse(
                quiz.getId(), quiz.getTitle(), attemptedCount, notAttempted.size(), notAttempted,
                average, highest, lowest, passPercentage, buckets, table);
    }
}
