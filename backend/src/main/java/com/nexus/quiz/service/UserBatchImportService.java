package com.nexus.quiz.service;

import com.nexus.quiz.dto.admin.BulkUserResponse;
import com.nexus.quiz.dto.admin.CreatedUserResponse;
import com.nexus.quiz.dto.admin.UserSummaryResponse;
import com.nexus.quiz.entity.Role;
import com.nexus.quiz.entity.SchoolClass;
import com.nexus.quiz.entity.Student;
import com.nexus.quiz.entity.User;
import com.nexus.quiz.exception.ConflictException;
import com.nexus.quiz.repository.SchoolClassRepository;
import com.nexus.quiz.repository.AcademicYearRepository;
import com.nexus.quiz.repository.StudentRepository;
import com.nexus.quiz.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UserBatchImportService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AuditService auditService;

    public UserBatchImportService(
            UserRepository userRepository,
            StudentRepository studentRepository,
            SchoolClassRepository schoolClassRepository,
            AcademicYearRepository academicYearRepository,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.academicYearRepository = academicYearRepository;
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchResult processChunk(Long adminUserId, List<PreparedUser> preparedUsers) {
        List<CreatedUserResponse> created = new ArrayList<>();
        List<BulkUserResponse.RowFailure> failures = new ArrayList<>();
        Set<String> usernamesInChunk = new HashSet<>();
        Set<String> emailsInChunk = new HashSet<>();

        for (PreparedUser prepared : preparedUsers) {
            try {
                validateDuplicates(prepared, usernamesInChunk, emailsInChunk);
                SchoolClass schoolClass = resolveClass(prepared);
                User user = User.builder()
                        .fullName(prepared.fullName())
                        .username(prepared.username().trim())
                        .email(normalizeOptional(prepared.email()))
                        .phone(normalizeOptional(prepared.phone()))
                        .passwordHash(prepared.passwordHash())
                        .role(prepared.role())
                        .isActive(true)
                        .mustChangePassword(true)
                        .failedLoginCount(0)
                        .build();
                user = userRepository.save(user);

                if (prepared.role() == Role.STUDENT) {
                    studentRepository.save(Student.builder()
                            .user(user)
                            .schoolClass(schoolClass)
                            .rollNumber(normalizeOptional(prepared.rollNumber()))
                            .admissionNumber(normalizeOptional(prepared.admissionNumber()))
                            .build());
                }
                usernamesInChunk.add(prepared.username().trim().toLowerCase(Locale.ROOT));
                String normalizedEmail = normalizeOptional(prepared.email());
                if (normalizedEmail != null) {
                    emailsInChunk.add(normalizedEmail.toLowerCase(Locale.ROOT));
                }
                auditService.record(
                        adminUserId,
                        "USER_CREATED",
                        "USER",
                        user.getId(),
                        null,
                        java.util.Map.of(
                                "fullName", user.getFullName(),
                                "username", user.getUsername(),
                                "role", user.getRole().name()));

                created.add(new CreatedUserResponse(toSummary(user, schoolClass),
                        prepared.temporaryPassword()));
            } catch (RowRejectedException exception) {
                failures.add(new BulkUserResponse.RowFailure(prepared.rowNumber(), exception.getMessage()));
            }
        }
        return new BatchResult(created, failures);
    }

    private void validateDuplicates(
            PreparedUser prepared,
            Set<String> usernamesInChunk,
            Set<String> emailsInChunk) {
        String normalizedUsername = prepared.username().trim().toLowerCase(Locale.ROOT);
        if (usernamesInChunk.contains(normalizedUsername)
                || userRepository.existsByUsernameIgnoreCase(prepared.username().trim())) {
            throw new RowRejectedException("Username already exists");
        }
        String email = normalizeOptional(prepared.email());
        if (email != null) {
            String normalizedEmail = email.toLowerCase(Locale.ROOT);
            if (emailsInChunk.contains(normalizedEmail)
                    || userRepository.existsByEmailIgnoreCase(email)) {
                throw new RowRejectedException("Email already exists");
            }
        }
    }

    private SchoolClass resolveClass(PreparedUser prepared) {
        if (prepared.role() != Role.STUDENT) {
            return null;
        }
        List<SchoolClass> classes = academicYearRepository.findByIsCurrentTrue()
                .map(year -> schoolClassRepository.findByNameIgnoreCaseAndSectionIgnoreCaseAndAcademicYearId(
                        prepared.className(), prepared.section(), year.getId()))
                .orElseGet(() -> schoolClassRepository
                        .findByNameIgnoreCaseAndSectionIgnoreCase(prepared.className(), prepared.section()));
        if (classes.isEmpty()) {
            throw new RowRejectedException("Class and section were not found");
        }
        if (classes.size() > 1) {
            throw new RowRejectedException("Class and section are ambiguous across academic years");
        }
        return classes.getFirst();
    }

    private UserSummaryResponse toSummary(User user, SchoolClass schoolClass) {
        Student student = studentRepository.findByUserId(user.getId()).orElse(null);
        return new UserSummaryResponse(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                Boolean.TRUE.equals(user.getIsActive()),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                student == null ? null : student.getId(),
                schoolClass == null ? null : schoolClass.getId(),
                schoolClass == null ? null : schoolClass.getName(),
                schoolClass == null ? null : schoolClass.getSection(),
                student == null ? null : student.getRollNumber(),
                student == null ? null : student.getAdmissionNumber());
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record PreparedUser(
            int rowNumber,
            String fullName,
            String username,
            String email,
            String phone,
            Role role,
            String className,
            String section,
            String rollNumber,
            String admissionNumber,
            String temporaryPassword,
            String passwordHash) {
    }

    public record BatchResult(
            List<CreatedUserResponse> createdUsers,
            List<BulkUserResponse.RowFailure> failures) {
    }

    private static final class RowRejectedException extends ConflictException {
        private RowRejectedException(String message) {
            super(message);
        }
    }
}
