package com.nexus.quiz.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.quiz.entity.Role;
import com.nexus.quiz.entity.User;
import com.nexus.quiz.repository.RefreshTokenRepository;
import com.nexus.quiz.repository.UserRepository;
import com.nexus.quiz.security.JwtService;
import com.nexus.quiz.util.PasswordUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthControllerIntegrationTest.AdminTestConfiguration.class)
class AuthControllerIntegrationTest {

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String REFRESH_URL = "/api/v1/auth/refresh";
    private static final String USER_PASSWORD = "SecurePass1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private final Set<String> ownedUsernames = new HashSet<>();
    private ListAppender<ILoggingEvent> logAppender;
    private Logger rootLogger;

    @BeforeEach
    void verifyIsolatedDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().contains("/nexus_quiz_test"),
                    "Auth integration tests must use the nexus_quiz_test database");
        }
    }

    @AfterEach
    void cleanUpCreatedDataAndLogCapture() {
        for (String username : ownedUsernames) {
            userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(username, username)
                    .ifPresent(user -> {
                        refreshTokenRepository.deleteAll(
                                refreshTokenRepository.findAll().stream()
                                        .filter(token -> token.getUserId().equals(user.getId()))
                                        .toList());
                        userRepository.delete(user);
                    });
        }
        ownedUsernames.clear();

        if (logAppender != null && rootLogger != null) {
            rootLogger.detachAppender(logAppender);
            logAppender.stop();
            logAppender = null;
        }
    }

    @Test
    void loginReturnsAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
        User user = createUser(Role.STUDENT, true, false, USER_PASSWORD);

        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user.getUsername(), USER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andReturn();

        String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.startsWith("refreshToken="));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Path=/api/v1/auth"));
    }

    @Test
    void wrongPasswordAndUnknownUsernameUseSameUnauthorizedMessage() throws Exception {
        User user = createUser(Role.STUDENT, true, false, USER_PASSWORD);

        String wrongPasswordMessage = loginFailureMessage(
                user.getUsername(), "WrongPassword1", 401);
        String unknownUsernameMessage = loginFailureMessage(
                uniqueUsername(), USER_PASSWORD, 401);

        assertEquals("Invalid credentials", wrongPasswordMessage);
        assertEquals(wrongPasswordMessage, unknownUsernameMessage);
    }

    @Test
    void locksAfterFiveFailuresAndAllowsLoginAfterLockExpires() throws Exception {
        User user = createUser(Role.STUDENT, true, false, USER_PASSWORD);

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(user.getUsername(), "WrongPassword1")))
                    .andExpect(status().isUnauthorized());
        }

        User lockedUser = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(5, lockedUser.getFailedLoginCount());
        assertNotNull(lockedUser.getLockedUntilUtc());
        assertTrue(ChronoUnit.SECONDS.between(Instant.now(), lockedUser.getLockedUntilUtc()) > 14 * 60);
        assertTrue(ChronoUnit.SECONDS.between(Instant.now(), lockedUser.getLockedUntilUtc()) <= 15 * 60);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user.getUsername(), USER_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("IST")));

        lockedUser.setLockedUntilUtc(Instant.now().minusSeconds(1));
        userRepository.saveAndFlush(lockedUser);
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user.getUsername(), USER_PASSWORD)))
                .andExpect(status().isOk());

        User unlockedUser = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(0, unlockedUser.getFailedLoginCount());
        assertNull(unlockedUser.getLockedUntilUtc());
    }

    @Test
    void inactiveUserCannotLogin() throws Exception {
        User user = createUser(Role.STUDENT, false, false, USER_PASSWORD);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user.getUsername(), USER_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void refreshRotatesCookieAndRejectsOldToken() throws Exception {
        User user = createUser(Role.STUDENT, true, false, USER_PASSWORD);
        MvcResult login = login(user);
        String originalCookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String originalToken = cookieValue(originalCookie);

        MvcResult refresh = mockMvc.perform(post(REFRESH_URL).cookie(
                        new jakarta.servlet.http.Cookie("refreshToken", originalToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String rotatedToken = cookieValue(refresh.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertNotEquals(originalToken, rotatedToken);

        mockMvc.perform(post(REFRESH_URL).cookie(
                        new jakarta.servlet.http.Cookie("refreshToken", originalToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        User user = createUser(Role.STUDENT, true, false, USER_PASSWORD);
        MvcResult login = login(user);
        String refreshToken = cookieValue(login.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        String accessToken = responseData(login).get("accessToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post(REFRESH_URL)
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forcedPasswordChangeRejectsWeakPasswordAndClearsFlagOnSuccess() throws Exception {
        User user = createUser(Role.STUDENT, true, true, USER_PASSWORD);
        MvcResult login = login(user);
        String accessToken = responseData(login).get("accessToken").asText();
        String refreshToken = cookieValue(login.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                                .content(passwordChangeBody(USER_PASSWORD, "weak")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fieldErrors.newPassword").exists());

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChangeBody(USER_PASSWORD, "NewPassword2")))
                .andExpect(status().isOk());

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertFalse(updated.getMustChangePassword());
        assertTrue(PasswordUtil.verify("NewPassword2", updated.getPasswordHash()));
    }

    @Test
    void unauthenticatedMeReturnsJsonEnvelopeAndInvalidTokensReturnUnauthorized() throws Exception {
        User user = createUser(Role.STUDENT, true, false, USER_PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));

        String expired = expiredToken(user);
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());

        String validToken = jwtService.generateAccessToken(user);
        String tampered = validToken + "tampered";
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminTokenGetsForbiddenFromAdminOnlyEndpoint() throws Exception {
        User user = createUser(Role.STUDENT, true, false, USER_PASSWORD);
        mockMvc.perform(get("/api/v1/admin/test-access")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateAccessToken(user)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void passwordHashesAreBcryptAndNeverAppearInResponsesOrLogs() throws Exception {
        User user = createUser(Role.STUDENT, true, false, USER_PASSWORD);
        String passwordHash = user.getPasswordHash();
        assertTrue(passwordHash.startsWith("$2a$") || passwordHash.startsWith("$2b$"));

        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger.addAppender(logAppender);

        MvcResult response = login(user);
        String body = response.getResponse().getContentAsString();
        assertFalse(body.contains(USER_PASSWORD));
        assertFalse(body.contains(passwordHash));

        assertTrue(logAppender.list.stream().noneMatch(event ->
                event.getFormattedMessage().contains(USER_PASSWORD)
                        || event.getFormattedMessage().contains(passwordHash)));
    }

    private User createUser(Role role, boolean active, boolean mustChangePassword, String password) {
        String username = uniqueUsername();
        ownedUsernames.add(username);
        User user = User.builder()
                .fullName("Auth Test User")
                .username(username)
                .email(username + "@example.test")
                .passwordHash(PasswordUtil.hash(password))
                .role(role)
                .isActive(active)
                .mustChangePassword(mustChangePassword)
                .failedLoginCount(0)
                .build();
        return userRepository.saveAndFlush(user);
    }

    private String uniqueUsername() {
        return "auth-" + UUID.randomUUID().toString().replace("-", "");
    }

    private MvcResult login(User user) throws Exception {
        return mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(user.getUsername(), USER_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String loginFailureMessage(String username, String password, int expectedStatus)
            throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, password)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("message").asText();
    }

    private String loginBody(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(
                new LoginPayload(username, password));
    }

    private String passwordChangeBody(String current, String next) throws Exception {
        return objectMapper.writeValueAsString(
                new PasswordChangePayload(current, next));
    }

    private com.fasterxml.jackson.databind.JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String cookieValue(String setCookieHeader) {
        assertNotNull(setCookieHeader);
        String cookiePair = setCookieHeader.split(";", 2)[0];
        return cookiePair.substring(cookiePair.indexOf('=') + 1);
    }

    private String expiredToken(User user) {
        Instant issuedAt = Instant.now().minus(60, ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(30, ChronoUnit.MINUTES)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    private record LoginPayload(String usernameOrEmail, String password) {
    }

    private record PasswordChangePayload(String currentPassword, String newPassword) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AdminTestConfiguration {
        @Bean
        AdminOnlyTestEndpoint adminOnlyTestEndpoint() {
            return new AdminOnlyTestEndpoint();
        }
    }

    @RestController
    static class AdminOnlyTestEndpoint {
        @GetMapping("/api/v1/admin/test-access")
        @PreAuthorize("hasRole('ADMIN')")
        public String adminOnly() {
            return "admin";
        }
    }
}
