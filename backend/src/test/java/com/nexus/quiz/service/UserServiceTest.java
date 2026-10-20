package com.nexus.quiz.service;

import com.nexus.quiz.dto.admin.BulkUserResponse;
import com.nexus.quiz.dto.admin.CreateUserRequest;
import com.nexus.quiz.dto.admin.CreatedUserResponse;
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
        verify(refreshTokenRepository).revokeOtherActiveTokens(12L, null);
        verify(auditService).record(
                eq(3L), eq("USER_DEACTIVATED"), eq("USER"), eq(12L), any(), any());
    }

    @Test
    void bulkUploadReportsBadRowsAndStillReturnsCreatedUsers() throws Exception {
        String csv = "fullName,username,email,phone\n"
                + "Good User,good.user,good@example.test,123\n"
                + ",bad.user,bad@example.test,456\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "users.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        CreatedUserResponse created = new CreatedUserResponse(null, "Temporary123");
        when(batchImportService.processChunk(eq(3L), any()))
                .thenReturn(new UserBatchImportService.BatchResult(List.of(created), List.of()));
        when(auditService.record(any(), any(), any(), any(), any(), any())).thenReturn(null);

        BulkUserResponse response = userService.bulkUploadUsers(3L, file, Role.FACULTY);

        assertEquals(1, response.successCount());
        assertEquals(1, response.failureCount());
        assertEquals(3, response.failures().getFirst().rowNumber());
        assertEquals(1, response.createdUsers().size());
        verify(batchImportService).processChunk(eq(3L), any());
    }
}
