package com.nexus.quiz.service;

import com.nexus.quiz.dto.admin.ParentStudentLinkRequest;
import com.nexus.quiz.dto.admin.FacultyAssignmentRequest;
import com.nexus.quiz.entity.Role;
import com.nexus.quiz.entity.AcademicYear;
import com.nexus.quiz.entity.ParentStudent;
import com.nexus.quiz.entity.SchoolClass;
import com.nexus.quiz.entity.Student;
import com.nexus.quiz.entity.Subject;
import com.nexus.quiz.entity.User;
import com.nexus.quiz.exception.ConflictException;
import com.nexus.quiz.repository.AcademicYearRepository;
import com.nexus.quiz.repository.FacultyAssignmentRepository;
import com.nexus.quiz.repository.ParentStudentRepository;
import com.nexus.quiz.repository.QuestionUsageRepository;
import com.nexus.quiz.repository.SchoolClassRepository;
import com.nexus.quiz.repository.StudentRepository;
import com.nexus.quiz.repository.SubjectRepository;
import com.nexus.quiz.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AcademicServiceTest {

    @Mock
    private AcademicYearRepository academicYearRepository;
    @Mock
    private SchoolClassRepository schoolClassRepository;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private FacultyAssignmentRepository facultyAssignmentRepository;
    @Mock
    private ParentStudentRepository parentStudentRepository;
    @Mock
    private QuestionUsageRepository questionUsageRepository;
    @Mock
    private AuditService auditService;

    private AcademicService academicService;

    @BeforeEach
    void setUp() {
        academicService = new AcademicService(
                academicYearRepository, schoolClassRepository, subjectRepository,
                userRepository, studentRepository, facultyAssignmentRepository,
                parentStudentRepository, questionUsageRepository, auditService);
    }

    @Test
    void refusesThirdParentForStudent() {
        User parent = User.builder().id(10L).role(Role.PARENT).build();
        Student student = Student.builder().id(20L).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(parent));
        when(studentRepository.findById(20L)).thenReturn(Optional.of(student));
        when(parentStudentRepository.existsByParentIdAndStudentId(10L, 20L)).thenReturn(false);
        when(parentStudentRepository.countByStudentId(20L)).thenReturn(2L);

        assertThrows(ConflictException.class, () -> academicService.linkParentToStudent(
                1L,
                new ParentStudentLinkRequest(10L, 20L,
                        com.nexus.quiz.entity.ParentStudent.Relation.GUARDIAN)));
    }

    @Test
    void listsParentLinksFilteredByStudentUsingPagination() {
        when(parentStudentRepository.findByStudentId(
                org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        var result = academicService.listParentStudentLinks(0, 20, "id,asc", 20L);

        assertEquals(0, result.totalElements());
        verify(parentStudentRepository).findByStudentId(
                org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deletingClassWithStudentsReturnsConflict() {
        when(schoolClassRepository.findById(7L)).thenReturn(Optional.of(
                com.nexus.quiz.entity.SchoolClass.builder()
                        .id(7L)
                        .name("Class 5")
                        .section("A")
                        .academicYearId(2L)
                        .build()));
        when(studentRepository.existsBySchoolClassId(7L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> academicService.deleteClass(1L, 7L));
        verify(schoolClassRepository, org.mockito.Mockito.never())
                .delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void duplicateFacultyAssignmentReturnsConflict() {
        User faculty = User.builder().id(10L).role(Role.FACULTY).build();
        SchoolClass schoolClass = SchoolClass.builder().id(20L).build();
        Subject subject = Subject.builder().id(30L).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(faculty));
        when(schoolClassRepository.findById(20L)).thenReturn(Optional.of(schoolClass));
        when(subjectRepository.findById(30L)).thenReturn(Optional.of(subject));
        when(facultyAssignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(
                10L, 20L, 30L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> academicService.createFacultyAssignment(
                1L, new FacultyAssignmentRequest(10L, 20L, 30L)));
        verify(facultyAssignmentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void creatingCurrentAcademicYearClearsThePreviouslyCurrentYear() {
        AcademicYear currentYear = AcademicYear.builder()
                .id(4L)
                .name("2024-2025")
                .startDate(LocalDate.of(2024, 6, 1))
                .endDate(LocalDate.of(2025, 4, 30))
                .isCurrent(true)
                .build();
        when(academicYearRepository.existsByNameIgnoreCase("2025-2026")).thenReturn(false);
        when(academicYearRepository.findByIsCurrentTrue()).thenReturn(Optional.of(currentYear));
        when(academicYearRepository.save(org.mockito.ArgumentMatchers.any(AcademicYear.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var created = academicService.createAcademicYear(1L, new com.nexus.quiz.dto.admin.AcademicYearRequest(
                "2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30), true));

        assertEquals("2025-2026", created.name());
        assertEquals(false, currentYear.getIsCurrent());
        verify(academicYearRepository, org.mockito.Mockito.times(2))
                .save(org.mockito.ArgumentMatchers.any(AcademicYear.class));
        verify(auditService).record(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("ACADEMIC_YEAR_UPDATED"),
                org.mockito.ArgumentMatchers.eq("ACADEMIC_YEAR"),
                org.mockito.ArgumentMatchers.eq(4L),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyMap());
        verify(auditService).record(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("ACADEMIC_YEAR_CREATED"),
                org.mockito.ArgumentMatchers.eq("ACADEMIC_YEAR"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void oneParentCanBeLinkedToMultipleStudents() {
        User parent = User.builder().id(10L).fullName("Parent").role(Role.PARENT).build();
        Student firstStudent = Student.builder().id(20L)
                .user(User.builder().id(30L).fullName("First Student").build()).build();
        Student secondStudent = Student.builder().id(21L)
                .user(User.builder().id(31L).fullName("Second Student").build()).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(parent));
        when(studentRepository.findById(20L)).thenReturn(Optional.of(firstStudent));
        when(studentRepository.findById(21L)).thenReturn(Optional.of(secondStudent));
        when(parentStudentRepository.existsByParentIdAndStudentId(10L, 20L)).thenReturn(false);
        when(parentStudentRepository.existsByParentIdAndStudentId(10L, 21L)).thenReturn(false);
        when(parentStudentRepository.countByStudentId(20L)).thenReturn(0L);
        when(parentStudentRepository.countByStudentId(21L)).thenReturn(0L);
        when(parentStudentRepository.save(org.mockito.ArgumentMatchers.any(ParentStudent.class)))
                .thenAnswer(invocation -> {
                    ParentStudent link = invocation.getArgument(0);
                    link.setId(link.getStudent().getId());
                    return link;
                });

        var firstLink = academicService.linkParentToStudent(1L,
                new ParentStudentLinkRequest(10L, 20L, ParentStudent.Relation.GUARDIAN));
        var secondLink = academicService.linkParentToStudent(1L,
                new ParentStudentLinkRequest(10L, 21L, ParentStudent.Relation.GUARDIAN));

        assertEquals(20L, firstLink.studentId());
        assertEquals(21L, secondLink.studentId());
        verify(parentStudentRepository, org.mockito.Mockito.times(2))
                .save(org.mockito.ArgumentMatchers.any(ParentStudent.class));
    }

    @Test
    void facultyAssignmentRequiresFacultyRole() {
        User parent = User.builder().id(10L).role(Role.PARENT).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(parent));

        assertThrows(ConflictException.class, () -> academicService.createFacultyAssignment(
                1L, new FacultyAssignmentRequest(10L, 20L, 30L)));

        verify(schoolClassRepository, never()).findById(20L);
        verify(facultyAssignmentRepository, never())
                .save(org.mockito.ArgumentMatchers.any());
    }
}
