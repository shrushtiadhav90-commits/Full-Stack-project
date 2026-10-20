package com.nexus.quiz.service;

import com.nexus.quiz.dto.admin.AcademicYearRequest;
import com.nexus.quiz.dto.admin.AcademicYearResponse;
import com.nexus.quiz.dto.admin.FacultyAssignmentRequest;
import com.nexus.quiz.dto.admin.FacultyAssignmentResponse;
import com.nexus.quiz.dto.admin.PageResponse;
import com.nexus.quiz.dto.admin.ParentStudentLinkRequest;
import com.nexus.quiz.dto.admin.ParentStudentLinkResponse;
import com.nexus.quiz.dto.admin.SchoolClassRequest;
import com.nexus.quiz.dto.admin.SchoolClassResponse;
import com.nexus.quiz.dto.admin.SubjectRequest;
import com.nexus.quiz.dto.admin.SubjectResponse;
import com.nexus.quiz.entity.AcademicYear;
import com.nexus.quiz.entity.FacultyAssignment;
import com.nexus.quiz.entity.ParentStudent;
import com.nexus.quiz.entity.Role;
import com.nexus.quiz.entity.SchoolClass;
import com.nexus.quiz.entity.Student;
import com.nexus.quiz.entity.Subject;
import com.nexus.quiz.entity.User;
import com.nexus.quiz.exception.ConflictException;
import com.nexus.quiz.exception.ResourceNotFoundException;
import com.nexus.quiz.repository.AcademicYearRepository;
import com.nexus.quiz.repository.FacultyAssignmentRepository;
import com.nexus.quiz.repository.ParentStudentRepository;
import com.nexus.quiz.repository.QuestionUsageRepository;
import com.nexus.quiz.repository.SchoolClassRepository;
import com.nexus.quiz.repository.StudentRepository;
import com.nexus.quiz.repository.SubjectRepository;
import com.nexus.quiz.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AcademicService {

    private static final Pattern CLASS_NAME =
            Pattern.compile("Class (?:[1-9]|1[0-2])");

    private final AcademicYearRepository academicYearRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final FacultyAssignmentRepository facultyAssignmentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final QuestionUsageRepository questionUsageRepository;
    private final AuditService auditService;

    public AcademicService(
            AcademicYearRepository academicYearRepository,
            SchoolClassRepository schoolClassRepository,
            SubjectRepository subjectRepository,
            UserRepository userRepository,
            StudentRepository studentRepository,
            FacultyAssignmentRepository facultyAssignmentRepository,
            ParentStudentRepository parentStudentRepository,
            QuestionUsageRepository questionUsageRepository,
            AuditService auditService) {
        this.academicYearRepository = academicYearRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.facultyAssignmentRepository = facultyAssignmentRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.questionUsageRepository = questionUsageRepository;
        this.auditService = auditService;
    }

    @Transactional
    public AcademicYearResponse createAcademicYear(Long adminUserId, AcademicYearRequest request) {
        validateYearDates(request);
        if (academicYearRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new ConflictException("Academic year name already exists");
        }
        if (request.current()) {
            clearCurrentAcademicYear(adminUserId, null);
        }
        AcademicYear year = AcademicYear.builder()
                .name(request.name().trim())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .isCurrent(request.current())
                .build();
        year = academicYearRepository.save(year);
        auditService.record(adminUserId, "ACADEMIC_YEAR_CREATED", "ACADEMIC_YEAR", year.getId(),
                null, yearAuditValue(year));
        return toResponse(year);
    }

    @Transactional
    public AcademicYearResponse updateAcademicYear(
            Long adminUserId,
            Long yearId,
            AcademicYearRequest request) {
        validateYearDates(request);
        AcademicYear year = academicYearRepository.findById(yearId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic year not found"));
        if (academicYearRepository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), yearId)) {
            throw new ConflictException("Academic year name already exists");
        }
        Map<String, Object> oldValue = yearAuditValue(year);
        if (request.current()) {
            clearCurrentAcademicYear(adminUserId, yearId);
        }
        year.setName(request.name().trim());
        year.setStartDate(request.startDate());
        year.setEndDate(request.endDate());
        year.setIsCurrent(request.current());
        year = academicYearRepository.save(year);
        auditService.record(adminUserId, "ACADEMIC_YEAR_UPDATED", "ACADEMIC_YEAR", yearId,
                oldValue, yearAuditValue(year));
        return toResponse(year);
    }

    @Transactional(readOnly = true)
    public PageResponse<AcademicYearResponse> listAcademicYears(int page, int size, String sort) {
        Pageable pageable = pageable(page, size, sort, Set.of("id", "name", "startDate", "endDate"));
        return PageResponse.from(academicYearRepository.findAllBy(pageable).map(this::toResponse));
    }

    @Transactional
    public SchoolClassResponse createClass(Long adminUserId, SchoolClassRequest request) {
        validateClassName(request.name());
        requireAcademicYear(request.academicYearId());
        if (schoolClassRepository.existsByNameIgnoreCaseAndSectionIgnoreCaseAndAcademicYearId(
                request.name().trim(), request.section().trim(), request.academicYearId())) {
            throw new ConflictException("Class and section already exist for this academic year");
        }
        SchoolClass schoolClass = SchoolClass.builder()
                .name(request.name().trim())
                .section(request.section().trim())
                .academicYearId(request.academicYearId())
                .build();
        schoolClass = schoolClassRepository.save(schoolClass);
        auditService.record(adminUserId, "CLASS_CREATED", "CLASS", schoolClass.getId(),
                null, classAuditValue(schoolClass));
        return toResponse(schoolClass);
    }

    @Transactional
    public SchoolClassResponse updateClass(Long adminUserId, Long classId, SchoolClassRequest request) {
        validateClassName(request.name());
        requireAcademicYear(request.academicYearId());
        SchoolClass schoolClass = requireClass(classId);
        if (schoolClassRepository.existsByNameIgnoreCaseAndSectionIgnoreCaseAndAcademicYearIdAndIdNot(
                request.name().trim(), request.section().trim(), request.academicYearId(), classId)) {
            throw new ConflictException("Class and section already exist for this academic year");
        }
        Map<String, Object> oldValue = classAuditValue(schoolClass);
        schoolClass.setName(request.name().trim());
        schoolClass.setSection(request.section().trim());
        schoolClass.setAcademicYearId(request.academicYearId());
        schoolClass = schoolClassRepository.save(schoolClass);
        auditService.record(adminUserId, "CLASS_UPDATED", "CLASS", classId,
                oldValue, classAuditValue(schoolClass));
        return toResponse(schoolClass);
    }

    @Transactional(readOnly = true)
    public PageResponse<SchoolClassResponse> listClasses(int page, int size, String sort) {
        Pageable pageable = pageable(page, size, sort, Set.of("id", "name", "section", "academicYearId"));
        return PageResponse.from(schoolClassRepository.findAllBy(pageable).map(this::toResponse));
    }

    @Transactional
    public void deleteClass(Long adminUserId, Long classId) {
        SchoolClass schoolClass = requireClass(classId);
        if (studentRepository.existsBySchoolClassId(classId)) {
            throw new ConflictException("Class cannot be deleted while it has enrolled students");
        }
        Map<String, Object> oldValue = classAuditValue(schoolClass);
        try {
            schoolClassRepository.delete(schoolClass);
            schoolClassRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Class is still referenced by other records");
        }
        auditService.record(adminUserId, "CLASS_DELETED", "CLASS", classId, oldValue, null);
    }

    @Transactional
    public SubjectResponse createSubject(Long adminUserId, SubjectRequest request) {
        String code = normalizeOptional(request.code());
        if (code == null) {
            code = generateSubjectCode(request.name());
        }
        if (subjectRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Subject code already exists");
        }
        Subject subject = Subject.builder()
                .name(request.name().trim())
                .code(code)
                .build();
        subject = subjectRepository.save(subject);
        auditService.record(adminUserId, "SUBJECT_CREATED", "SUBJECT", subject.getId(),
                null, subjectAuditValue(subject));
        return toResponse(subject);
    }

    @Transactional
    public SubjectResponse updateSubject(Long adminUserId, Long subjectId, SubjectRequest request) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));
        String code = normalizeOptional(request.code());
        if (code == null) {
            code = subject.getCode();
        }
        if (subjectRepository.existsByCodeIgnoreCaseAndIdNot(code, subjectId)) {
            throw new ConflictException("Subject code already exists");
        }
        Map<String, Object> oldValue = subjectAuditValue(subject);
        subject.setName(request.name().trim());
        subject.setCode(code);
        subject = subjectRepository.save(subject);
        auditService.record(adminUserId, "SUBJECT_UPDATED", "SUBJECT", subjectId,
                oldValue, subjectAuditValue(subject));
        return toResponse(subject);
    }

    @Transactional(readOnly = true)
    public PageResponse<SubjectResponse> listSubjects(int page, int size, String sort) {
        Pageable pageable = pageable(page, size, sort, Set.of("id", "name", "code"));
        return PageResponse.from(subjectRepository.findAllBy(pageable).map(this::toResponse));
    }

    @Transactional
    public void deleteSubject(Long adminUserId, Long subjectId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));
        if (facultyAssignmentRepository.existsBySubjectId(subjectId)
                || questionUsageRepository.existsBySubjectId(subjectId)) {
            throw new ConflictException("Subject cannot be deleted while assigned or used by questions");
        }
        Map<String, Object> oldValue = subjectAuditValue(subject);
        try {
            subjectRepository.delete(subject);
            subjectRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Subject is still referenced by other records");
        }
        auditService.record(adminUserId, "SUBJECT_DELETED", "SUBJECT", subjectId, oldValue, null);
    }

    @Transactional
    public FacultyAssignmentResponse createFacultyAssignment(
            Long adminUserId,
            FacultyAssignmentRequest request) {
        User faculty = requireUser(request.facultyUserId());
        if (faculty.getRole() != Role.FACULTY) {
            throw new ConflictException("Assigned user must have the FACULTY role");
        }
        SchoolClass schoolClass = requireClass(request.classId());
        Subject subject = subjectRepository.findById(request.subjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));
        if (facultyAssignmentRepository.existsByFacultyIdAndSchoolClassIdAndSubjectId(
                faculty.getId(), schoolClass.getId(), subject.getId())) {
            throw new ConflictException("Faculty assignment already exists");
        }
        FacultyAssignment assignment = facultyAssignmentRepository.save(FacultyAssignment.builder()
                .faculty(faculty)
                .schoolClass(schoolClass)
                .subject(subject)
                .build());
        auditService.record(adminUserId, "FACULTY_ASSIGNMENT_CREATED", "FACULTY_ASSIGNMENT",
                assignment.getId(), null, assignmentAuditValue(assignment));
        return toResponse(assignment);
    }

    @Transactional(readOnly = true)
    public PageResponse<FacultyAssignmentResponse> listFacultyAssignments(int page, int size, String sort) {
        Pageable pageable = pageable(page, size, sort, Set.of("id", "createdAtUtc"));
        return PageResponse.from(facultyAssignmentRepository.findAllBy(pageable).map(this::toResponse));
    }

    @Transactional
    public void deleteFacultyAssignment(Long adminUserId, Long assignmentId) {
        FacultyAssignment assignment = facultyAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Faculty assignment not found"));
        Map<String, Object> oldValue = assignmentAuditValue(assignment);
        facultyAssignmentRepository.delete(assignment);
        auditService.record(adminUserId, "FACULTY_ASSIGNMENT_DELETED", "FACULTY_ASSIGNMENT",
                assignmentId, oldValue, null);
    }

    @Transactional
    public ParentStudentLinkResponse linkParentToStudent(
            Long adminUserId,
            ParentStudentLinkRequest request) {
        User parent = requireUser(request.parentUserId());
        if (parent.getRole() != Role.PARENT) {
            throw new ConflictException("Linked user must have the PARENT role");
        }
        Student student = studentRepository.findById(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        if (parentStudentRepository.existsByParentIdAndStudentId(parent.getId(), student.getId())) {
            throw new ConflictException("Parent is already linked to this student");
        }
        if (parentStudentRepository.countByStudentId(student.getId()) >= 2) {
            throw new ConflictException("A student cannot have more than two linked parents");
        }
        ParentStudent link = parentStudentRepository.save(ParentStudent.builder()
                .parent(parent)
                .student(student)
                .relation(request.relation())
                .build());
        auditService.record(adminUserId, "PARENT_STUDENT_LINK_CREATED", "PARENT_STUDENT",
                link.getId(), null, parentStudentAuditValue(link));
        return toResponse(link);
    }

    @Transactional(readOnly = true)
    public PageResponse<ParentStudentLinkResponse> listParentStudentLinks(int page, int size, String sort) {
        Pageable pageable = pageable(page, size, sort, Set.of("id", "createdAtUtc"));
        return PageResponse.from(parentStudentRepository.findAllBy(pageable).map(this::toResponse));
    }

    @Transactional
    public void unlinkParentFromStudent(Long adminUserId, Long linkId) {
        ParentStudent link = parentStudentRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent-student link not found"));
        Map<String, Object> oldValue = parentStudentAuditValue(link);
        parentStudentRepository.delete(link);
        auditService.record(adminUserId, "PARENT_STUDENT_LINK_DELETED", "PARENT_STUDENT",
                linkId, oldValue, null);
    }

    private void clearCurrentAcademicYear(Long adminUserId, Long exceptId) {
        academicYearRepository.findByIsCurrentTrue()
                .filter(current -> !current.getId().equals(exceptId))
                .ifPresent(current -> {
                    Map<String, Object> oldValue = yearAuditValue(current);
                    current.setIsCurrent(false);
                    AcademicYear saved = academicYearRepository.save(current);
                    auditService.record(adminUserId, "ACADEMIC_YEAR_UPDATED", "ACADEMIC_YEAR",
                            saved.getId(), oldValue, yearAuditValue(saved));
                });
    }

    private void validateYearDates(AcademicYearRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("Academic year end date must not precede its start date");
        }
    }

    private void validateClassName(String name) {
        if (name == null || !CLASS_NAME.matcher(name.trim()).matches()) {
            throw new IllegalArgumentException("Class name must be Class 1 through Class 12");
        }
    }

    private AcademicYear requireAcademicYear(Long id) {
        return academicYearRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Academic year not found"));
    }

    private SchoolClass requireClass(Long id) {
        return schoolClassRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found"));
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Pageable pageable(int page, int size, String sort, Set<String> allowedFields) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size must be from 1 to 100");
        }
        String[] values = sort == null || sort.isBlank() ? new String[]{"id", "asc"} : sort.split(",", 2);
        String property = values[0].trim();
        if (!allowedFields.contains(property)) {
            throw new IllegalArgumentException("Unsupported sort field");
        }
        Sort.Direction direction = values.length == 2
                ? Sort.Direction.fromString(values[1].trim())
                : Sort.Direction.ASC;
        return PageRequest.of(page, size, Sort.by(direction, property));
    }

    private String generateSubjectCode(String name) {
        String code = name.replaceAll("[^A-Za-z0-9]", "").toUpperCase(java.util.Locale.ROOT);
        if (code.isBlank()) {
            throw new IllegalArgumentException("Subject requires a name that can produce a code");
        }
        return code.length() > 20 ? code.substring(0, 20) : code;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AcademicYearResponse toResponse(AcademicYear year) {
        return new AcademicYearResponse(year.getId(), year.getName(), year.getStartDate(),
                year.getEndDate(), Boolean.TRUE.equals(year.getIsCurrent()));
    }

    private SchoolClassResponse toResponse(SchoolClass schoolClass) {
        return new SchoolClassResponse(schoolClass.getId(), schoolClass.getName(),
                schoolClass.getSection(), schoolClass.getAcademicYearId());
    }

    private SubjectResponse toResponse(Subject subject) {
        return new SubjectResponse(subject.getId(), subject.getName(), subject.getCode());
    }

    private FacultyAssignmentResponse toResponse(FacultyAssignment assignment) {
        return new FacultyAssignmentResponse(
                assignment.getId(),
                assignment.getFaculty().getId(),
                assignment.getFaculty().getFullName(),
                assignment.getSchoolClass().getId(),
                assignment.getSchoolClass().getName(),
                assignment.getSchoolClass().getSection(),
                assignment.getSubject().getId(),
                assignment.getSubject().getName());
    }

    private ParentStudentLinkResponse toResponse(ParentStudent link) {
        return new ParentStudentLinkResponse(
                link.getId(),
                link.getParent().getId(),
                link.getParent().getFullName(),
                link.getStudent().getId(),
                link.getStudent().getUser().getFullName(),
                link.getRelation());
    }

    private Map<String, Object> yearAuditValue(AcademicYear year) {
        return Map.of("name", year.getName(),
                "startDate", year.getStartDate().toString(),
                "endDate", year.getEndDate().toString(),
                "current", Boolean.TRUE.equals(year.getIsCurrent()));
    }

    private Map<String, Object> classAuditValue(SchoolClass schoolClass) {
        return Map.of("name", schoolClass.getName(),
                "section", schoolClass.getSection(),
                "academicYearId", schoolClass.getAcademicYearId());
    }

    private Map<String, Object> subjectAuditValue(Subject subject) {
        return Map.of("name", subject.getName(), "code", subject.getCode());
    }

    private Map<String, Object> assignmentAuditValue(FacultyAssignment assignment) {
        return Map.of("facultyUserId", assignment.getFaculty().getId(),
                "classId", assignment.getSchoolClass().getId(),
                "subjectId", assignment.getSubject().getId());
    }

    private Map<String, Object> parentStudentAuditValue(ParentStudent link) {
        return Map.of("parentUserId", link.getParent().getId(),
                "studentId", link.getStudent().getId(),
                "relation", link.getRelation().name());
    }
}
