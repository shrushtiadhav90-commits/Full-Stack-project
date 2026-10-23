package com.nexus.quiz.service;

import com.nexus.quiz.entity.*;
import com.nexus.quiz.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuizReportServiceTest {

    @Test
    void reportUsesBestGradedAttemptCountsNonAttemptersAndMarksPartialAttemptsPending() {
        User faculty = User.builder().id(1L).role(Role.FACULTY).build();
        Subject subject = Subject.builder().id(10L).name("Math").build();
        SchoolClass schoolClass = SchoolClass.builder().id(20L).name("Class 6").section("A").build();
        Quiz quiz = Quiz.builder().id(30L).createdBy(faculty).subject(subject)
                .title("Quiz").passPercentage(new BigDecimal("40.00")).build();
        Student one = student(101L, "A Learner", schoolClass);
        Student two = student(102L, "B Learner", schoolClass);
        Student three = student(103L, "C Learner", schoolClass);

        QuizRepository quizRepository = mock(QuizRepository.class);
        QuizClassRepository quizClassRepository = mock(QuizClassRepository.class);
        QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        FacultyAssignmentRepository assignmentRepository = mock(FacultyAssignmentRepository.class);
        FacultyIdentity identity = mock(FacultyIdentity.class);
        when(identity.requireFacultyId()).thenReturn(1L);
        when(quizRepository.findById(30L)).thenReturn(Optional.of(quiz));
        when(quizClassRepository.findByQuizIdOrderByIdAsc(30L)).thenReturn(List.of(
                QuizClass.builder().quiz(quiz).schoolClass(schoolClass).build()));
        when(assignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(1L, 20L, 10L))
                .thenReturn(true);
        when(studentRepository.findBySchoolClassIdInOrderByUserFullNameAsc(List.of(20L)))
                .thenReturn(List.of(one, two, three));
        when(attemptRepository.findByQuizIdOrderByIdAsc(30L)).thenReturn(List.of(
                attempt(8L, one, quiz, AttemptStatus.GRADED, "90.00"),
                attempt(9L, one, quiz, AttemptStatus.GRADED, "90.00"),
                attempt(10L, three, quiz, AttemptStatus.GRADED, "30.00"),
                attempt(11L, three, quiz, AttemptStatus.SUBMITTED, null)));
        QuizReportService service = new QuizReportService(
                quizRepository, quizClassRepository, attemptRepository, studentRepository,
                assignmentRepository, identity);

        var report = service.getQuizReport(30L);

        assertEquals(2, report.attemptedCount());
        assertEquals(1, report.notAttemptedCount());
        assertEquals("B Learner", report.studentsNotAttempted().getFirst().studentName());
        assertEquals("Class 6", report.studentsNotAttempted().getFirst().className());
        assertEquals("A", report.studentsNotAttempted().getFirst().section());
        assertEquals(new BigDecimal("60.00"), report.classAverage());
        assertEquals(new BigDecimal("90.00"), report.highestPercentage());
        assertEquals(new BigDecimal("30.00"), report.lowestPercentage());
        assertEquals(new BigDecimal("50.00"), report.passPercentage());
        assertEquals(new BigDecimal("90.00"), report.students().getFirst().bestAttemptPercentage());
        assertEquals(2, report.students().getFirst().attemptsUsed());
        assertEquals("PENDING", report.students().get(2).status());
        assertEquals(10, report.scoreDistribution().size());
        assertEquals(1, report.scoreDistribution().get(3).count());
        assertEquals(1, report.scoreDistribution().get(9).count());
    }

    @Test
    void refusesReportsForQuizzesOwnedByAnotherFaculty() {
        Quiz quiz = Quiz.builder().id(30L)
                .createdBy(User.builder().id(2L).role(Role.FACULTY).build())
                .subject(Subject.builder().id(10L).build()).build();
        QuizRepository quizRepository = mock(QuizRepository.class);
        FacultyIdentity identity = mock(FacultyIdentity.class);
        when(identity.requireFacultyId()).thenReturn(1L);
        when(quizRepository.findById(30L)).thenReturn(Optional.of(quiz));
        QuizReportService service = new QuizReportService(
                quizRepository, mock(QuizClassRepository.class), mock(QuizAttemptRepository.class),
                mock(StudentRepository.class), mock(FacultyAssignmentRepository.class), identity);

        assertThrows(AccessDeniedException.class, () -> service.getQuizReport(30L));
    }

    private Student student(Long id, String name, SchoolClass schoolClass) {
        return Student.builder().id(id).user(User.builder().id(id + 1000)
                .fullName(name).build()).schoolClass(schoolClass).build();
    }

    private QuizAttempt attempt(
            Long id, Student student, Quiz quiz, AttemptStatus status, String percentage) {
        return QuizAttempt.builder().id(id).student(student).quiz(quiz).status(status)
                .startedAt(Instant.now().minusSeconds(30))
                .percentage(percentage == null ? null : new BigDecimal(percentage))
                .build();
    }
}
