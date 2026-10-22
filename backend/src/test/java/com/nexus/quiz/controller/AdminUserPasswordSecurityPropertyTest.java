package com.nexus.quiz.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.quiz.NexusQuizApplication;
import com.nexus.quiz.entity.AuditLog;
import com.nexus.quiz.entity.Role;
import com.nexus.quiz.entity.User;
import com.nexus.quiz.repository.AuditLogRepository;
import com.nexus.quiz.repository.RefreshTokenRepository;
import com.nexus.quiz.repository.StudentRepository;
import com.nexus.quiz.repository.UserRepository;
import com.nexus.quiz.security.JwtService;
import com.nexus.quiz.util.PasswordUtil;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Provide;
import net.jqwik.api.Property;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.lifecycle.BeforeTry;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserPasswordSecurityPropertyTest {

    private MockMvc mockMvc;
    private UserRepository userRepository;
    private StudentRepository studentRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private AuditLogRepository auditLogRepository;
    private JwtService jwtService;
    private ObjectMapper objectMapper;

    private ConfigurableApplicationContext applicationContext;
    private DataSource dataSource;
    private final List<String> createdUsernames = new ArrayList<>();
    private ListAppender<ILoggingEvent> logAppender;
    private Logger rootLogger;

    @BeforeProperty
    void startTestProfileApplication() {
        applicationContext = new SpringApplicationBuilder(NexusQuizApplication.class)
                .profiles("test")
                .run();
        mockMvc = MockMvcBuilders.webAppContextSetup(
                (WebApplicationContext) applicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        userRepository = applicationContext.getBean(UserRepository.class);
        studentRepository = applicationContext.getBean(StudentRepository.class);
        refreshTokenRepository = applicationContext.getBean(RefreshTokenRepository.class);
        auditLogRepository = applicationContext.getBean(AuditLogRepository.class);
        jwtService = applicationContext.getBean(JwtService.class);
        objectMapper = applicationContext.getBean(ObjectMapper.class);
        dataSource = applicationContext.getBean(DataSource.class);
    }

    @AfterProperty
    void stopTestProfileApplication() {
        if (applicationContext != null) applicationContext.close();
    }

    @BeforeTry
    void verifyTestDatabaseAndAttachLogAppender() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().contains("/nexus_quiz_test"),
                    "Password security properties must use nexus_quiz_test");
        }
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger.addAppender(logAppender);
    }

    @AfterTry
    void cleanOwnedUsersAndDetachLogAppender() {
        for (String username : createdUsernames) {
            userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(username, username)
                    .ifPresent(user -> {
                        auditLogRepository.findAll().stream()
                                .filter(log -> user.getId().equals(log.getTargetEntityId())
                                        && "USER".equals(log.getTargetEntityType()))
                                .forEach(auditLogRepository::delete);
                        refreshTokenRepository.deleteAll(refreshTokenRepository.findAll().stream()
                                .filter(token -> token.getUserId().equals(user.getId())).toList());
                        studentRepository.findByUserId(user.getId())
                                .ifPresent(studentRepository::delete);
                        userRepository.delete(user);
                    });
        }
        createdUsernames.clear();
        if (rootLogger != null && logAppender != null) {
            rootLogger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    @Provide
    Arbitrary<String> generatedIdentityParts() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(24);
    }

    @Property(tries = 100)
    void temporaryPasswordsAreNeverPersistedLoggedOrReturnedByGet(
            @ForAll("generatedIdentityParts") String input) throws Exception {
        String username = "pw-property-" + input.toLowerCase(Locale.ROOT) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        createdUsernames.add(username);
        String adminUsername = "test-seed-admin";
        User admin = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(
                adminUsername, adminUsername).orElseThrow();
        String authorization = "Bearer " + jwtService.generateAccessToken(admin);

        String createBody = objectMapper.writeValueAsString(java.util.Map.of(
                "fullName", "Property User " + input,
                "username", username,
                "email", username + "@example.test",
                "role", Role.PARENT.name()));
        String createJson = mockMvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createJson);
        assertSingleTemporaryPassword(createJson);
        String createPassword = created.path("data").path("temporaryPassword").asText();
        User persisted = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(
                username, username).orElseThrow();
        assertBcryptMatches(createPassword, persisted);
        assertAuditsDoNotContainPassword(persisted.getId(), createPassword);

        assertGetResponsesDoNotContainPasswordFieldsOrString(
                authorization, persisted.getId(), username, createPassword);

        String resetJson = mockMvc.perform(post(
                        "/api/v1/admin/users/{id}/reset-password", persisted.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertSingleTemporaryPassword(resetJson);
        String resetPassword = objectMapper.readTree(resetJson)
                .path("data").path("temporaryPassword").asText();
        assertFalse(createPassword.equals(resetPassword));
        User resetUser = userRepository.findById(persisted.getId()).orElseThrow();
        assertBcryptMatches(resetPassword, resetUser);
        assertAuditsDoNotContainPassword(persisted.getId(), resetPassword);
        assertFalse(logOutput().contains(createPassword));
        assertFalse(logOutput().contains(resetPassword));
    }

    private void assertGetResponsesDoNotContainPasswordFieldsOrString(
            String authorization, Long userId, String username, String password) throws Exception {
        String listJson = mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .param("search", username))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String detailJson = mockMvc.perform(get("/api/v1/admin/users/{id}", userId)
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String auditJson = mockMvc.perform(get("/api/v1/admin/audit-log")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .param("actionType", "USER_CREATED")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String response : List.of(listJson, detailJson, auditJson)) {
            JsonNode json = objectMapper.readTree(response);
            assertNoPasswordFields(json);
            assertFalse(response.contains(password));
        }
    }

    private void assertAuditsDoNotContainPassword(Long userId, String password) {
        List<AuditLog> userAudits = auditLogRepository.findAll().stream()
                .filter(log -> userId.equals(log.getTargetEntityId())
                        && "USER".equals(log.getTargetEntityType()))
                .toList();
        assertFalse(userAudits.isEmpty());
        for (AuditLog log : userAudits) {
            for (String value : List.of(
                    log.getOldValue() == null ? "" : log.getOldValue(),
                    log.getNewValue() == null ? "" : log.getNewValue())) {
                assertFalse(value.contains(password));
                if (!value.isBlank()) {
                    try {
                        assertNoPasswordFields(objectMapper.readTree(value));
                    } catch (Exception exception) {
                        throw new AssertionError("Audit JSON should remain valid JSON", exception);
                    }
                }
            }
        }
    }

    private void assertBcryptMatches(String password, User user) {
        assertTrue(user.getPasswordHash().startsWith("$2a$")
                || user.getPasswordHash().startsWith("$2b$"));
        assertTrue(PasswordUtil.verify(password, user.getPasswordHash()));
    }

    private void assertSingleTemporaryPassword(String response) throws Exception {
        JsonNode json = objectMapper.readTree(response);
        assertEquals(1, countNamedField(json, "temporaryPassword"));
    }

    private int countNamedField(JsonNode node, String name) {
        if (node.isObject()) {
            int count = 0;
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (name.equals(field.getKey())) count++;
                count += countNamedField(field.getValue(), name);
            }
            return count;
        }
        if (node.isArray()) {
            int count = 0;
            for (JsonNode child : node) count += countNamedField(child, name);
            return count;
        }
        return 0;
    }

    private void assertNoPasswordFields(JsonNode node) {
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String normalized = field.getKey().toLowerCase(Locale.ROOT);
                assertFalse(normalized.contains("password")
                                && !normalized.equals("mustchangepassword"),
                        "Unexpected password field: " + field.getKey());
                assertFalse(normalized.equals("token"), "Unexpected token field in GET response");
                assertNoPasswordFields(field.getValue());
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) assertNoPasswordFields(child);
        }
    }

    private String logOutput() {
        return logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
    }
}
