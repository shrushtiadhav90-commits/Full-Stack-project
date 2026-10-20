package com.nexus.quiz.service;

import com.nexus.quiz.dto.admin.BulkUserResponse;
import com.nexus.quiz.dto.admin.CreateUserRequest;
import com.nexus.quiz.dto.admin.CreatedUserResponse;
import com.nexus.quiz.dto.admin.EditUserRequest;
import com.nexus.quiz.dto.admin.PageResponse;
import com.nexus.quiz.dto.admin.UserSummaryResponse;
import com.nexus.quiz.entity.Role;
import com.nexus.quiz.entity.SchoolClass;
import com.nexus.quiz.entity.Student;
import com.nexus.quiz.entity.User;
import com.nexus.quiz.exception.ConflictException;
import com.nexus.quiz.exception.ResourceNotFoundException;
import com.nexus.quiz.repository.RefreshTokenRepository;
import com.nexus.quiz.repository.SchoolClassRepository;
import com.nexus.quiz.repository.StudentRepository;
import com.nexus.quiz.repository.UserRepository;
import com.nexus.quiz.util.PasswordUtil;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.tika.Tika;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class UserService {

    private static final long MAX_CSV_BYTES = 5L * 1024 * 1024;
    private static final int BULK_CHUNK_SIZE = 50;
    private static final Pattern CLASS_AND_SECTION =
            Pattern.compile("^\\s*(Class\\s+(?:[1-9]|1[0-2]))\\s*(?:-|,|\\s)\\s*([A-Za-z0-9]{1,10})\\s*$",
                    Pattern.CASE_INSENSITIVE);
    private static final Map<Role, List<String>> CSV_HEADERS = Map.of(
            Role.STUDENT, List.of("fullName", "username", "email", "phone",
                    "classNameAndSection", "rollNumber", "admissionNumber"),
            Role.FACULTY, List.of("fullName", "username", "email", "phone"),
            Role.PARENT, List.of("fullName", "username", "email", "phone"));

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;
    private final UserBatchImportService userBatchImportService;
    private final Tika tika = new Tika();

    public UserService(
            UserRepository userRepository,
            StudentRepository studentRepository,
            SchoolClassRepository schoolClassRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuditService auditService,
            UserBatchImportService userBatchImportService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
        this.userBatchImportService = userBatchImportService;
    }

    @Transactional
    public CreatedUserResponse createUser(Long adminUserId, CreateUserRequest request) {
        validateUniqueIdentity(request.username(), request.email(), null);
        SchoolClass schoolClass = findClassForStudent(request.role(), request.classId());
        String temporaryPassword = PasswordUtil.generate();
        User user = User.builder()
                .fullName(request.fullName().trim())
                .username(request.username().trim())
                .email(normalizeOptional(request.email()))
                .phone(normalizeOptional(request.phone()))
                .passwordHash(PasswordUtil.hash(temporaryPassword))
                .role(request.role())
                .isActive(true)
                .mustChangePassword(true)
                .failedLoginCount(0)
                .build();
        user = userRepository.save(user);

        if (request.role() == Role.STUDENT) {
            studentRepository.save(Student.builder()
                    .user(user)
                    .schoolClass(schoolClass)
                    .rollNumber(normalizeOptional(request.rollNumber()))
                    .admissionNumber(normalizeOptional(request.admissionNumber()))
                    .build());
        }

        auditService.record(adminUserId, "USER_CREATED", "USER", user.getId(), null,
                userAuditValue(user, schoolClass));
        return new CreatedUserResponse(toSummary(user, schoolClass), temporaryPassword);
    }

    @Transactional
    public UserSummaryResponse editUser(Long adminUserId, Long userId, EditUserRequest request) {
        User user = findUser(userId);
        validateUniqueIdentity(request.username(), request.email(), userId);

        Map<String, Object> oldValue = userAuditValue(user, studentRepository.findByUserId(userId)
                .map(Student::getSchoolClass).orElse(null));
        user.setFullName(request.fullName().trim());
        user.setUsername(request.username().trim());
        user.setEmail(normalizeOptional(request.email()));
        user.setPhone(normalizeOptional(request.phone()));

        SchoolClass schoolClass = null;
        if (user.getRole() == Role.STUDENT) {
            Student student = studentRepository.findByUserId(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
            schoolClass = request.classId() == null
                    ? student.getSchoolClass()
                    : findClassForStudent(user.getRole(), request.classId());
            student.setSchoolClass(schoolClass);
            student.setRollNumber(normalizeOptional(request.rollNumber()));
            student.setAdmissionNumber(normalizeOptional(request.admissionNumber()));
            studentRepository.save(student);
        } else if (request.classId() != null) {
            throw new ConflictException("Only student accounts can be assigned to a class");
        }

        user = userRepository.save(user);
        auditService.record(adminUserId, "USER_UPDATED", "USER", userId, oldValue,
                userAuditValue(user, schoolClass));
        return toSummary(user, schoolClass);
    }

    @Transactional
    public UserSummaryResponse deactivateUser(Long adminUserId, Long userId) {
        if (adminUserId != null && adminUserId.equals(userId)) {
            throw new ConflictException("An admin cannot deactivate their own account");
        }
        User user = findUser(userId);
        if (user.getRole() == Role.ADMIN && Boolean.TRUE.equals(user.getIsActive())
                && userRepository.countByRoleAndIsActiveTrue(Role.ADMIN) <= 1) {
            throw new ConflictException("The last active admin cannot be deactivated");
        }
        user.setIsActive(false);
        userRepository.save(user);
        refreshTokenRepository.revokeOtherActiveTokens(userId, null);
        auditService.record(adminUserId, "USER_DEACTIVATED", "USER", userId,
                Map.of("isActive", true), Map.of("isActive", false));
        return toSummary(user, studentClassFor(userId));
    }

    @Transactional
    public UserSummaryResponse activateUser(Long adminUserId, Long userId) {
        User user = findUser(userId);
        user.setIsActive(true);
        userRepository.save(user);
        auditService.record(adminUserId, "USER_ACTIVATED", "USER", userId,
                Map.of("isActive", false), Map.of("isActive", true));
        return toSummary(user, studentClassFor(userId));
    }

    @Transactional
    public String resetPassword(Long adminUserId, Long userId) {
        User user = findUser(userId);
        String temporaryPassword = PasswordUtil.generate();
        user.setPasswordHash(PasswordUtil.hash(temporaryPassword));
        user.setMustChangePassword(true);
        user.setFailedLoginCount(0);
        user.setLockedUntilUtc(null);
        userRepository.save(user);
        refreshTokenRepository.revokeOtherActiveTokens(userId, null);
        auditService.record(adminUserId, "USER_PASSWORD_RESET", "USER", userId,
                null, Map.of("mustChangePassword", true));
        return temporaryPassword;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> listUsers(
            int page,
            int size,
            String sort,
            Role role,
            Boolean active,
            String search) {
        Pageable pageable = pageable(page, size, sort, Set.of(
                "id", "fullName", "username", "email", "role", "createdAtUtc"));
        Specification<User> specification = Specification.where(null);
        if (role != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("role"), role));
        }
        if (active != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("isActive"), active));
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("fullName")), pattern),
                    builder.like(builder.lower(root.get("username")), pattern),
                    builder.like(builder.lower(root.get("email")), pattern)));
        }
        Page<UserSummaryResponse> result = userRepository.findAll(specification, pageable)
                .map(user -> toSummary(user, studentClassFor(user.getId())));
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse getUser(Long userId) {
        User user = findUser(userId);
        return toSummary(user, studentClassFor(userId));
    }

    public BulkUserResponse bulkUploadUsers(Long adminUserId, MultipartFile file, Role role)
            throws IOException {
        List<String> headers = headersFor(role);
        validateCsvFile(file);
        List<BulkUserResponse.RowFailure> failures = new ArrayList<>();
        List<CreatedUserResponse> createdUsers = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVReader csvReader = new CSVReaderBuilder(reader).build()) {
            String[] header = csvReader.readNext();
            if (header == null || !headers.equals(Arrays.stream(header)
                    .map(String::trim).toList())) {
                throw new IllegalArgumentException("CSV header does not match the selected role template");
            }

            List<UserBatchImportService.PreparedUser> chunk = new ArrayList<>(BULK_CHUNK_SIZE);
            int rowNumber = 1;
            String[] row;
            while ((row = csvReader.readNext()) != null) {
                rowNumber++;
                UserBatchImportService.PreparedUser prepared = prepareRow(rowNumber, row, role, headers.size(), failures);
                if (prepared != null) {
                    chunk.add(prepared);
                } else {
                    failureCount++;
                }

                if (chunk.size() == BULK_CHUNK_SIZE) {
                    UserBatchImportService.BatchResult result =
                            userBatchImportService.processChunk(adminUserId, List.copyOf(chunk));
                    createdUsers.addAll(result.createdUsers());
                    failures.addAll(result.failures());
                    successCount += result.createdUsers().size();
                    failureCount += result.failures().size();
                    chunk.clear();
                }
            }
            if (!chunk.isEmpty()) {
                UserBatchImportService.BatchResult result =
                        userBatchImportService.processChunk(adminUserId, List.copyOf(chunk));
                createdUsers.addAll(result.createdUsers());
                failures.addAll(result.failures());
                successCount += result.createdUsers().size();
                failureCount += result.failures().size();
            }
        } catch (CsvValidationException exception) {
            throw new IllegalArgumentException("CSV file contains an invalid row", exception);
        }

        auditService.record(adminUserId, "USER_BULK_UPLOAD", "USER", null, null,
                Map.of("role", role.name(), "successCount", successCount, "failureCount", failureCount));
        return new BulkUserResponse(successCount, failureCount, List.copyOf(failures),
                List.copyOf(createdUsers));
    }

    public String csvTemplate(Role role) {
        return String.join(",", headersFor(role)) + "\r\n";
    }

    private UserBatchImportService.PreparedUser prepareRow(
            int rowNumber,
            String[] row,
            Role role,
            int expectedColumns,
            List<BulkUserResponse.RowFailure> failures) {
        if (row.length != expectedColumns) {
            failures.add(new BulkUserResponse.RowFailure(rowNumber, "Expected " + expectedColumns + " columns"));
            return null;
        }
        for (int index : new int[]{0, 1}) {
            if (row[index] == null || row[index].isBlank()) {
                failures.add(new BulkUserResponse.RowFailure(rowNumber,
                        headersFor(role).get(index) + " is required"));
                return null;
            }
        }
        String lengthFailure = fieldLengthFailure(row, role);
        if (lengthFailure != null) {
            failures.add(new BulkUserResponse.RowFailure(rowNumber, lengthFailure));
            return null;
        }
        String email = cell(row, 2);
        if (email != null && !email.isBlank()
                && !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            failures.add(new BulkUserResponse.RowFailure(rowNumber, "Email is invalid"));
            return null;
        }

        String className = null;
        String section = null;
        String rollNumber = null;
        String admissionNumber = null;
        if (role == Role.STUDENT) {
            Matcher matcher = CLASS_AND_SECTION.matcher(cell(row, 4) == null ? "" : cell(row, 4));
            if (!matcher.matches()) {
                failures.add(new BulkUserResponse.RowFailure(rowNumber,
                        "classNameAndSection must look like Class 1 - A"));
                return null;
            }
            className = matcher.group(1).replaceAll("\\s+", " ").trim();
            section = matcher.group(2).trim();
            rollNumber = cell(row, 5);
            admissionNumber = cell(row, 6);
        }

        String temporaryPassword = PasswordUtil.generate();
        String passwordHash = PasswordUtil.hash(temporaryPassword);
        return new UserBatchImportService.PreparedUser(
                rowNumber,
                row[0].trim(),
                row[1].trim(),
                email,
                cell(row, 3),
                role,
                className,
                section,
                rollNumber,
                admissionNumber,
                temporaryPassword,
                passwordHash);
    }

    private void validateCsvFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file must not be empty");
        }
        if (file.getSize() > MAX_CSV_BYTES) {
            throw new IllegalArgumentException("CSV file must not exceed 5 MB");
        }
        String contentType;
        try (var input = file.getInputStream()) {
            contentType = tika.detect(input);
        }
        if (!"text/csv".equals(contentType) && !"application/csv".equals(contentType)
                && !"text/plain".equals(contentType)) {
            throw new IllegalArgumentException("Uploaded file is not a valid CSV");
        }
    }

    private List<String> headersFor(Role role) {
        List<String> headers = CSV_HEADERS.get(role);
        if (headers == null) {
            throw new IllegalArgumentException("Bulk uploads support STUDENT, FACULTY, and PARENT roles only");
        }
        return headers;
    }

    private String fieldLengthFailure(String[] row, Role role) {
        int[] limits = role == Role.STUDENT
                ? new int[]{100, 50, 150, 20, 100, 20, 30}
                : new int[]{100, 50, 150, 20};
        for (int index = 0; index < row.length; index++) {
            if (row[index] != null && row[index].length() > limits[index]) {
                return headersFor(role).get(index) + " exceeds " + limits[index] + " characters";
            }
        }
        return null;
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

    private void validateUniqueIdentity(String username, String email, Long userId) {
        boolean duplicateUsername = userId == null
                ? userRepository.existsByUsernameIgnoreCase(username.trim())
                : userRepository.existsByUsernameIgnoreCaseAndIdNot(username.trim(), userId);
        if (duplicateUsername) {
            throw new ConflictException("Username already exists");
        }
        String normalizedEmail = normalizeOptional(email);
        if (normalizedEmail != null) {
            boolean duplicateEmail = userId == null
                    ? userRepository.existsByEmailIgnoreCase(normalizedEmail)
                    : userRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, userId);
            if (duplicateEmail) {
                throw new ConflictException("Email already exists");
            }
        }
    }

    private SchoolClass findClassForStudent(Role role, Long classId) {
        if (role != Role.STUDENT) {
            if (classId != null) {
                throw new ConflictException("Only student accounts can be assigned to a class");
            }
            return null;
        }
        if (classId == null) {
            throw new IllegalArgumentException("A class is required for student accounts");
        }
        return schoolClassRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found"));
    }

    private SchoolClass studentClassFor(Long userId) {
        return studentRepository.findByUserId(userId)
                .map(Student::getSchoolClass)
                .orElse(null);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private UserSummaryResponse toSummary(User user, SchoolClass schoolClass) {
        return new UserSummaryResponse(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                Boolean.TRUE.equals(user.getIsActive()),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                schoolClass == null ? null : schoolClass.getId(),
                schoolClass == null ? null : schoolClass.getName(),
                schoolClass == null ? null : schoolClass.getSection());
    }

    private Map<String, Object> userAuditValue(User user, SchoolClass schoolClass) {
        Map<String, Object> value = new HashMap<>();
        value.put("fullName", user.getFullName());
        value.put("username", user.getUsername());
        value.put("email", user.getEmail());
        value.put("phone", user.getPhone());
        value.put("role", user.getRole().name());
        value.put("classId", schoolClass == null ? null : schoolClass.getId());
        return value;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String cell(String[] row, int index) {
        return row[index] == null || row[index].isBlank() ? null : row[index].trim();
    }
}
