package com.nexus.quiz.service;

import com.nexus.quiz.dto.admin.BulkUserResponse;
import com.nexus.quiz.dto.admin.CreateUserRequest;
import com.nexus.quiz.dto.admin.CreatedUserResponse;
import com.nexus.quiz.dto.admin.EditUserRequest;
import com.nexus.quiz.entity.Role;
import com.nexus.quiz.entity.User;
import com.nexus.quiz.exception.ConflictException;
import com.nexus.quiz.repository.RefreshTokenRepository;
import com.nexus.quiz.repository.SchoolClassRepository;
import com.nexus.quiz.repository.StudentRepository;
import com.nexus.quiz.repository.UserRepository;
import com.nexus.quiz.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private SchoolClassRepository schoolClassRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private UserBatchImportService batchImportService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository, studentRepository, schoolClassRepository,
                refreshTokenRepository, auditService, batchImportService);
    }

    @Test
    void createUserStoresBcryptAndReturnsTemporaryPasswordOnceWithoutAuditingIt() {
        when(userRepository.existsByUsernameIgnoreCase("new.faculty")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("faculty@example.test")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });
        CreateUserRequest request = new CreateUserRequest(
                "New Faculty", "new.faculty", "faculty@example.test", null,
                Role.FACULTY, null, null, null);

        CreatedUserResponse response = userService.createUser(3L, request);

        assertNotNull(response.temporaryPassword());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User stored = userCaptor.getValue();
        assertTrue(stored.getPasswordHash().startsWith("$2a$") || stored.getPasswordHash().startsWith("$2b$"));
        assertTrue(PasswordUtil.verify(response.temporaryPassword(), stored.getPasswordHash()));
        assertTrue(stored.getMustChangePassword());

        ArgumentCaptor<Map<String, ?>> auditCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(
                eq(3L), eq("USER_CREATED"), eq("USER"), eq(42L), eq(null), auditCaptor.capture());
        Map<String, ?> auditValue = auditCaptor.getValue();
        assertFalse(auditValue.containsKey("password"));
        assertFalse(auditValue.containsKey("passwordHash"));
        assertFalse(auditValue.containsValue(response.temporaryPassword()));
    }

    @Test
    void duplicateUsernameReturnsConflict() {
        when(userRepository.existsByUsernameIgnoreCase("existing")).thenReturn(true);
        CreateUserRequest request = new CreateUserRequest(
                "Existing User", "existing", null, null, Role.PARENT, null, null, null);

        assertThrows(ConflictException.class, () -> userService.createUser(3L, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void duplicateEmailReturnsConflict() {
        when(userRepository.existsByUsernameIgnoreCase("new.user")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("existing@example.test")).thenReturn(true);
        CreateUserRequest request = new CreateUserRequest(
                "New User", "new.user", "existing@example.test", null,
                Role.PARENT, null, null, null);

        assertThrows(ConflictException.class, () -> userService.createUser(3L, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void studentMustHaveAClass() {
        CreateUserRequest request = new CreateUserRequest(
                "New Student", "new.student", null, null,
                Role.STUDENT, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> userService.createUser(3L, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void editingUserCannotChangeTheirRole() {
        User faculty = User.builder()
                .id(13L)
                .fullName("Faculty Member")
                .username("faculty")
                .role(Role.FACULTY)
                .isActive(true)
                .mustChangePassword(false)
                .build();
        when(userRepository.findById(13L)).thenReturn(Optional.of(faculty));
        when(studentRepository.findByUserId(13L)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.editUser(3L, 13L, new EditUserRequest(
                "Updated Faculty", "faculty.updated", null, null, null, null, null));

        assertEquals(Role.FACULTY, faculty.getRole());
        assertEquals("Updated Faculty", faculty.getFullName());
    }

    @Test
    void deactivationRevokesRefreshTokens() {
        User user = User.builder()
                .id(12L)
                .fullName("Staff Member")
                .username("staff")
                .role(Role.FACULTY)
                .isActive(true)
                .mustChangePassword(false)
                .build();
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(studentRepository.findByUserId(12L)).thenReturn(Optional.empty());

        userService.deactivateUser(3L, 12L);

        assertFalse(user.getIsActive());
        assertEquals("Staff Member", user.getFullName());
        assertEquals(Role.FACULTY, user.getRole());
        verify(refreshTokenRepository).revokeOtherActiveTokens(12L, null);
        verify(auditService).record(
                eq(3L), eq("USER_DEACTIVATED"), eq("USER"), eq(12L), any(), any());
    }

    @Test
    void adminCannotDeactivateTheirOwnAccount() {
        assertThrows(ConflictException.class, () -> userService.deactivateUser(3L, 3L));
        verify(userRepository, never()).save(any());
    }

    @Test
    void lastActiveAdminCannotBeDeactivated() {
        User admin = User.builder()
                .id(12L)
                .fullName("Only Admin")
                .username("only.admin")
                .role(Role.ADMIN)
                .isActive(true)
                .build();
        when(userRepository.findById(12L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleAndIsActiveTrue(Role.ADMIN)).thenReturn(1L);

        assertThrows(ConflictException.class, () -> userService.deactivateUser(3L, 12L));
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPasswordClearsLockoutAndRequiresPasswordChange() {
        User user = User.builder()
                .id(15L)
                .fullName("Locked User")
                .username("locked.user")
                .role(Role.FACULTY)
                .passwordHash(PasswordUtil.hash("PreviousPassword1"))
                .isActive(true)
                .mustChangePassword(false)
                .failedLoginCount(5)
                .lockedUntilUtc(Instant.now().plusSeconds(900))
                .build();
        when(userRepository.findById(15L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String temporaryPassword = userService.resetPassword(3L, 15L);

        assertTrue(PasswordUtil.verify(temporaryPassword, user.getPasswordHash()));
        assertTrue(user.getMustChangePassword());
        assertEquals(0, user.getFailedLoginCount());
        assertEquals(null, user.getLockedUntilUtc());
        verify(refreshTokenRepository).revokeOtherActiveTokens(15L, null);
        verify(auditService).record(
                eq(3L), eq("USER_PASSWORD_RESET"), eq("USER"), eq(15L), eq(null), any());
    }

    @Test
    void bulkUploadReportsBadRowsAndStillReturnsCreatedUsers() throws Exception {
        String csv = "fullName,username,email,phone\n"
                + ",bad.user,bad@example.test,456\n"
                + "Good User,good.user,good@example.test,123\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "users.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        CreatedUserResponse created = new CreatedUserResponse(null, "Temporary123");
        when(batchImportService.processChunk(eq(3L), any()))
                .thenReturn(new UserBatchImportService.BatchResult(List.of(created), List.of()));
        when(auditService.record(any(), any(), any(), any(), any(), any())).thenReturn(null);

        BulkUserResponse response = userService.bulkUploadUsers(3L, file, Role.FACULTY);

        assertEquals(1, response.successCount());
        assertEquals(1, response.failureCount());
        assertEquals(2, response.failures().getFirst().rowNumber());
        assertTrue(response.failures().getFirst().reason().contains("fullName"));
        assertEquals(1, response.createdUsers().size());
        assertEquals("Temporary123", response.createdUsers().getFirst().temporaryPassword());
        verify(batchImportService).processChunk(eq(3L), any());
    }

    @Test
    void bulkUploadRejectsFilesLargerThanFiveMegabytes() {
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "users.csv", "text/csv", new byte[5 * 1024 * 1024 + 1]);

        assertThrows(IllegalArgumentException.class,
                () -> userService.bulkUploadUsers(3L, oversized, Role.FACULTY));
        verify(batchImportService, never()).processChunk(any(), any());
    }

    @Test
    void bulkUploadRejectsNonCsvContent() {
        MockMultipartFile notCsv = new MockMultipartFile(
                "file", "users.csv", "text/csv", new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        assertThrows(IllegalArgumentException.class,
                () -> userService.bulkUploadUsers(3L, notCsv, Role.FACULTY));
        verify(batchImportService, never()).processChunk(any(), any());
    }
}
