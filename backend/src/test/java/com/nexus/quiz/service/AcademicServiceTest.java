package com.nexus.quiz.service;

import com.nexus.quiz.dto.admin.ParentStudentLinkRequest;
import com.nexus.quiz.entity.Role;
import com.nexus.quiz.entity.Student;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
