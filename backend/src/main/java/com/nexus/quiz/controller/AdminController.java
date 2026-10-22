package com.nexus.quiz.controller;

import com.nexus.quiz.dto.ApiResponse;
import com.nexus.quiz.dto.admin.*;
import com.nexus.quiz.entity.Role;
import com.nexus.quiz.service.AcademicService;
import com.nexus.quiz.service.AuditLogQueryService;
import com.nexus.quiz.service.SettingsService;
import com.nexus.quiz.service.UserService;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private final UserService userService;
    private final AcademicService academicService;
    private final SettingsService settingsService;
    private final AuditLogQueryService auditLogQueryService;

    public AdminController(
            UserService userService,
            AcademicService academicService,
            SettingsService settingsService,
            AuditLogQueryService auditLogQueryService) {
        this.userService = userService;
        this.academicService = academicService;
        this.settingsService = settingsService;
        this.auditLogQueryService = auditLogQueryService;
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<CreatedUserResponse>> createUser(
            @AuthenticationPrincipal Long adminUserId,
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                "User created", userService.createUser(adminUserId, request)));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id,asc") String sort,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success("Users",
                userService.listUsers(page, size, sort, role, active, search)));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("User", userService.getUser(id)));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> editUser(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long id,
            @Valid @RequestBody EditUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "User updated", userService.editUser(adminUserId, id, request)));
    }

    @PatchMapping("/users/{id}/deactivate")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> deactivateUser(
            @AuthenticationPrincipal Long adminUserId, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "User deactivated", userService.deactivateUser(adminUserId, id)));
    }

    @PatchMapping("/users/{id}/activate")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> activateUser(
            @AuthenticationPrincipal Long adminUserId, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "User activated", userService.activateUser(adminUserId, id)));
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<ApiResponse<TemporaryPasswordResponse>> resetPassword(
            @AuthenticationPrincipal Long adminUserId, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Password reset",
                new TemporaryPasswordResponse(userService.resetPassword(adminUserId, id))));
    }

    @PostMapping(value = "/users/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BulkUserResponse>> bulkUploadUsers(
            @AuthenticationPrincipal Long adminUserId,
            @RequestPart("file") MultipartFile file,
            @RequestParam Role role) throws java.io.IOException {
        return ResponseEntity.ok(ApiResponse.success(
                "Bulk upload processed", userService.bulkUploadUsers(adminUserId, file, role)));
    }

    @GetMapping("/users/template/{role}")
    public ResponseEntity<ByteArrayResource> userTemplate(@PathVariable Role role) {
        if (role != Role.STUDENT && role != Role.PARENT && role != Role.FACULTY) {
            throw new IllegalArgumentException("CSV templates are available for STUDENT, PARENT, and FACULTY");
        }
        byte[] bytes = userService.csvTemplate(role).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(role.name().toLowerCase() + "-users-template.csv").build().toString())
                .body(new ByteArrayResource(bytes));
    }

    @PostMapping("/academic-years")
    public ResponseEntity<ApiResponse<AcademicYearResponse>> createAcademicYear(
            @AuthenticationPrincipal Long adminUserId, @Valid @RequestBody AcademicYearRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success("Academic year created",
                academicService.createAcademicYear(adminUserId, request)));
    }

    @PutMapping("/academic-years/{id}")
    public ResponseEntity<ApiResponse<AcademicYearResponse>> updateAcademicYear(
            @AuthenticationPrincipal Long adminUserId, @PathVariable Long id,
            @Valid @RequestBody AcademicYearRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Academic year updated",
                academicService.updateAcademicYear(adminUserId, id, request)));
    }

    @GetMapping("/academic-years")
    public ResponseEntity<ApiResponse<PageResponse<AcademicYearResponse>>> listAcademicYears(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id,asc") String sort) {
        return ResponseEntity.ok(ApiResponse.success("Academic years",
                academicService.listAcademicYears(page, size, sort)));
    }

    @PostMapping("/classes")
    public ResponseEntity<ApiResponse<SchoolClassResponse>> createClass(
            @AuthenticationPrincipal Long adminUserId, @Valid @RequestBody SchoolClassRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success("Class created",
                academicService.createClass(adminUserId, request)));
    }

    @PutMapping("/classes/{id}")
    public ResponseEntity<ApiResponse<SchoolClassResponse>> updateClass(
            @AuthenticationPrincipal Long adminUserId, @PathVariable Long id,
            @Valid @RequestBody SchoolClassRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Class updated",
                academicService.updateClass(adminUserId, id, request)));
    }

    @GetMapping("/classes")
    public ResponseEntity<ApiResponse<PageResponse<SchoolClassResponse>>> listClasses(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id,asc") String sort) {
        return ResponseEntity.ok(ApiResponse.success("Classes",
                academicService.listClasses(page, size, sort)));
    }

    @DeleteMapping("/classes/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteClass(
            @AuthenticationPrincipal Long adminUserId, @PathVariable Long id) {
        academicService.deleteClass(adminUserId, id);
        return ResponseEntity.ok(ApiResponse.success("Class deleted", null));
    }

    @PostMapping("/subjects")
    public ResponseEntity<ApiResponse<SubjectResponse>> createSubject(
            @AuthenticationPrincipal Long adminUserId, @Valid @RequestBody SubjectRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success("Subject created",
                academicService.createSubject(adminUserId, request)));
    }

    @PutMapping("/subjects/{id}")
    public ResponseEntity<ApiResponse<SubjectResponse>> updateSubject(
            @AuthenticationPrincipal Long adminUserId, @PathVariable Long id,
            @Valid @RequestBody SubjectRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Subject updated",
                academicService.updateSubject(adminUserId, id, request)));
    }

    @GetMapping("/subjects")
    public ResponseEntity<ApiResponse<PageResponse<SubjectResponse>>> listSubjects(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id,asc") String sort) {
        return ResponseEntity.ok(ApiResponse.success("Subjects",
                academicService.listSubjects(page, size, sort)));
    }

    @DeleteMapping("/subjects/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSubject(
            @AuthenticationPrincipal Long adminUserId, @PathVariable Long id) {
        academicService.deleteSubject(adminUserId, id);
        return ResponseEntity.ok(ApiResponse.success("Subject deleted", null));
    }

    @PostMapping("/faculty-assignments")
    public ResponseEntity<ApiResponse<FacultyAssignmentResponse>> createFacultyAssignment(
            @AuthenticationPrincipal Long adminUserId,
            @Valid @RequestBody FacultyAssignmentRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success("Faculty assignment created",
                academicService.createFacultyAssignment(adminUserId, request)));
    }

    @GetMapping("/faculty-assignments")
    public ResponseEntity<ApiResponse<PageResponse<FacultyAssignmentResponse>>> listFacultyAssignments(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id,asc") String sort,
            @RequestParam(required = false) Long facultyUserId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long subjectId) {
        return ResponseEntity.ok(ApiResponse.success("Faculty assignments",
                academicService.listFacultyAssignments(page, size, sort, facultyUserId, classId, subjectId)));
    }

    @DeleteMapping("/faculty-assignments/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFacultyAssignment(
            @AuthenticationPrincipal Long adminUserId, @PathVariable Long id) {
        academicService.deleteFacultyAssignment(adminUserId, id);
        return ResponseEntity.ok(ApiResponse.success("Faculty assignment deleted", null));
    }

    @PostMapping("/parent-links")
    public ResponseEntity<ApiResponse<ParentStudentLinkResponse>> createParentLink(
            @AuthenticationPrincipal Long adminUserId,
            @Valid @RequestBody ParentStudentLinkRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success("Parent linked to student",
                academicService.linkParentToStudent(adminUserId, request)));
    }

    @GetMapping("/parent-links")
    public ResponseEntity<ApiResponse<PageResponse<ParentStudentLinkResponse>>> listParentLinks(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id,asc") String sort,
            @RequestParam(required = false) Long studentId) {
        return ResponseEntity.ok(ApiResponse.success("Parent-student links",
                academicService.listParentStudentLinks(page, size, sort, studentId)));
    }

    @DeleteMapping("/parent-links/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteParentLink(
            @AuthenticationPrincipal Long adminUserId, @PathVariable Long id) {
        academicService.unlinkParentFromStudent(adminUserId, id);
        return ResponseEntity.ok(ApiResponse.success("Parent-student link deleted", null));
    }

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<SettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success("School settings", settingsService.getSettings()));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<SettingsResponse>> updateSettings(
            @AuthenticationPrincipal Long adminUserId,
            @Valid @RequestBody UpdateSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("School settings updated",
                settingsService.updateSettings(adminUserId, request)));
    }

    @PostMapping(value = "/settings/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SettingsResponse>> updateLogo(
            @AuthenticationPrincipal Long adminUserId, @RequestPart("file") MultipartFile file)
            throws java.io.IOException {
        return ResponseEntity.ok(ApiResponse.success(
                "School logo updated", settingsService.updateLogo(adminUserId, file)));
    }

    @GetMapping("/audit-log")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> listAuditLog(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(ApiResponse.success("Audit log",
                auditLogQueryService.list(page, size, actionType, parseIst(from, false), parseIst(to, true))));
    }

    private Instant parseIst(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (java.time.format.DateTimeParseException ignored) {
            try {
                LocalDate date = LocalDate.parse(value);
                return (endOfDay ? date.plusDays(1).atStartOfDay(IST).toInstant().minusNanos(1)
                        : date.atStartOfDay(IST).toInstant());
            } catch (java.time.format.DateTimeParseException exception) {
                throw new IllegalArgumentException("Audit dates must be ISO date or date-time values");
            }
        }
    }

    public record TemporaryPasswordResponse(String temporaryPassword) {
    }
}
