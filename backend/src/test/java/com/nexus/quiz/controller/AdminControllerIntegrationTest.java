package com.nexus.quiz.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.quiz.entity.Role;
import com.nexus.quiz.entity.User;
import com.nexus.quiz.repository.AcademicYearRepository;
import com.nexus.quiz.repository.AuditLogRepository;
import com.nexus.quiz.repository.ParentStudentRepository;
import com.nexus.quiz.repository.RefreshTokenRepository;
import com.nexus.quiz.repository.SchoolClassRepository;
import com.nexus.quiz.repository.StudentRepository;
import com.nexus.quiz.repository.UserRepository;
import com.nexus.quiz.security.JwtService;
import com.nexus.quiz.util.PasswordUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerIntegrationTest {

    private static final String ADMIN_USERNAME = "test-seed-admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ParentStudentRepository parentStudentRepository;

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    @Autowired
    private AcademicYearRepository academicYearRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    private final Set<String> ownedUsernames = new HashSet<>();
    private final List<Long> preexistingAuditIds = new ArrayList<>();
    private final List<Long> ownedParentLinkIds = new ArrayList<>();
    private final List<Long> ownedClassIds = new ArrayList<>();
    private final List<Long> ownedAcademicYearIds = new ArrayList<>();
    private User admin;

    @BeforeEach
    void verifyTestDatabaseAndCaptureState() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().contains("/nexus_quiz_test"),
                    "Admin controller tests must use nexus_quiz_test");
        }
        admin = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(
                ADMIN_USERNAME, ADMIN_USERNAME).orElseThrow();
        auditLogRepository.findAll().forEach(log -> preexistingAuditIds.add(log.getId()));
    }

    @AfterEach
    void cleanOwnedData() {
        auditLogRepository.findAll().stream()
                .filter(log -> !preexistingAuditIds.contains(log.getId()))
                .forEach(auditLogRepository::delete);
        ownedParentLinkIds.forEach(parentStudentRepository::deleteById);
        for (String username : ownedUsernames) {
            userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(username, username)
                    .ifPresent(user -> {
                        studentRepository.findByUserId(user.getId()).ifPresent(studentRepository::delete);
                        refreshTokenRepository.deleteAll(refreshTokenRepository.findAll().stream()
                                .filter(token -> token.getUserId().equals(user.getId())).toList());
                        userRepository.delete(user);
                    });
        }
        ownedClassIds.forEach(schoolClassRepository::deleteById);
        ownedAcademicYearIds.forEach(academicYearRepository::deleteById);
        ownedUsernames.clear();
        ownedParentLinkIds.clear();
        ownedClassIds.clear();
        ownedAcademicYearIds.clear();
        preexistingAuditIds.clear();
    }

    @Test
    void adminCanCreateUserAndTemporaryPasswordIsNotReturnedByList() throws Exception {
        String username = uniqueUsername();
        String token = adminToken();
        String createBody = """
                {"fullName":"Controller Test User","username":"%s","email":"%s@example.test",
                 "role":"PARENT"}
                """.formatted(username, username);

        var result = mockMvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value(username))
                .andReturn();
        ownedUsernames.add(username);

        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        String temporaryPassword = created.path("data").path("temporaryPassword").asText();
        String listBody = mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(listBody.contains("temporaryPassword"));
        assertFalse(listBody.contains(temporaryPassword));
        assertFalse(listBody.contains("passwordHash"));
    }

    @Test
    void studentLookupReturnsStudentIdRequiredToCreateParentLink() throws Exception {
        String token = adminToken();
        String yearName = "AY-" + UUID.randomUUID().toString().substring(0, 8);
        var yearResult = mockMvc.perform(post("/api/v1/admin/academic-years")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","startDate":"2098-06-01","endDate":"2099-04-30","current":false}
                                """.formatted(yearName)))
                .andExpect(status().isCreated())
                .andReturn();
        long academicYearId = objectMapper.readTree(yearResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        ownedAcademicYearIds.add(academicYearId);

        var classResult = mockMvc.perform(post("/api/v1/admin/classes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Class 1","section":"A","academicYearId":%d}
                                """.formatted(academicYearId)))
                .andExpect(status().isCreated())
                .andReturn();
        long classId = objectMapper.readTree(classResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        ownedClassIds.add(classId);

        String studentUsername = uniqueUsername();
        String parentUsername = uniqueUsername();
        ownedUsernames.add(studentUsername);
        ownedUsernames.add(parentUsername);
        var studentResult = mockMvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Student Lookup Test","username":"%s",
                                 "role":"STUDENT","classId":%d}
                                """.formatted(studentUsername, classId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode studentUser = objectMapper.readTree(studentResult.getResponse().getContentAsString())
                .path("data").path("user");
        long userId = studentUser.path("id").asLong();
        long studentId = studentUser.path("studentId").asLong();
        assertEquals(studentRepository.findByUserId(userId).orElseThrow().getId(), studentId);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("role", "STUDENT")
                        .param("search", studentUsername)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].studentId").value(studentId));

        var parentResult = mockMvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Parent Lookup Test","username":"%s","role":"PARENT"}
                                """.formatted(parentUsername)))
                .andExpect(status().isCreated())
                .andReturn();
        long parentUserId = objectMapper.readTree(parentResult.getResponse().getContentAsString())
                .path("data").path("user").path("id").asLong();

        var linkResult = mockMvc.perform(post("/api/v1/admin/parent-links")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentUserId":%d,"studentId":%d,"relation":"GUARDIAN"}
                                """.formatted(parentUserId, studentId)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode link = objectMapper.readTree(linkResult.getResponse().getContentAsString()).path("data");
        ownedParentLinkIds.add(link.path("id").asLong());
        assertEquals(studentId, link.path("studentId").asLong());
    }

    @Test
    void adminEndpointsEnforceAuthenticationAndRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        User student = createUser(Role.STUDENT);
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateAccessToken(student)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void publicSettingsReturnsOnlyThePublicFields() throws Exception {
        String body = mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        assertTrue(data.has("schoolName"));
        assertTrue(data.has("logoUrl"));
        assertTrue(data.has("primaryColor"));
        assertTrue(data.has("secondaryColor"));
        assertFalse(data.has("emailNotificationsEnabled"));
        assertFalse(data.has("leaderboardEnabled"));
    }

    @Test
    void adminSettingsDefaultsOptionalFeaturesToDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leaderboardEnabled").value(false))
                .andExpect(jsonPath("$.data.emailNotificationsEnabled").value(false));
    }

    @Test
    void invalidColorAndOversizedLogoAreRejected() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolName":"Nexus School","primaryColor":"blue",
                                 "secondaryColor":"#F59E0B","leaderboardEnabled":true,
                                 "emailNotificationsEnabled":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fieldErrors.primaryColor").exists());

        byte[] oversizedPng = new byte[2 * 1024 * 1024 + 1];
        oversizedPng[0] = (byte) 0x89;
        oversizedPng[1] = 0x50;
        oversizedPng[2] = 0x4e;
        oversizedPng[3] = 0x47;
        oversizedPng[4] = 0x0d;
        oversizedPng[5] = 0x0a;
        oversizedPng[6] = 0x1a;
        oversizedPng[7] = 0x0a;
        MockMultipartFile logo = new MockMultipartFile(
                "file", "large.png", "image/png", oversizedPng);
        mockMvc.perform(multipart("/api/v1/admin/settings/logo")
                        .file(logo)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pathTraversalFilenameIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/files/logo/{filename}", "invalid..name.png"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void auditLogListsTheUserCreation() throws Exception {
        String username = uniqueUsername();
        ownedUsernames.add(username);
        mockMvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Audited User","username":"%s",
                                 "email":"%s@example.test","role":"PARENT"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/audit-log")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .param("actionType", "USER_CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].actionType").value("USER_CREATED"))
                .andExpect(jsonPath("$.data.content[0].adminName").isNotEmpty())
                .andExpect(jsonPath("$.data.content[0].createdAt").exists());
    }

    private String adminToken() {
        return jwtService.generateAccessToken(admin);
    }

    private User createUser(Role role) {
        String username = uniqueUsername();
        ownedUsernames.add(username);
        return userRepository.saveAndFlush(User.builder()
                .fullName("Test " + role)
                .username(username)
                .email(username + "@example.test")
                .passwordHash(PasswordUtil.hash("TestPassword1"))
                .role(role)
                .isActive(true)
                .mustChangePassword(false)
                .failedLoginCount(0)
                .build());
    }

    private String uniqueUsername() {
        return "admin-controller-" + UUID.randomUUID().toString().replace("-", "");
    }
}
