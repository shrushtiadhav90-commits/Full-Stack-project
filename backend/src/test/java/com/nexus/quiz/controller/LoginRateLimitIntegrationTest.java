package com.nexus.quiz.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.quiz.entity.Role;
import com.nexus.quiz.entity.User;
import com.nexus.quiz.repository.RefreshTokenRepository;
import com.nexus.quiz.repository.UserRepository;
import com.nexus.quiz.security.RateLimitingFilter;
import com.nexus.quiz.util.PasswordUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.rate-limit.login.ip-requests-per-minute=10000",
        "app.rate-limit.login.username-requests-per-minute=5"
})
class LoginRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private FilterChainProxy filterChainProxy;

    private final Set<String> ownedUsernames = new HashSet<>();

    @BeforeEach
    void verifyIsolatedDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().contains("/nexus_quiz_test"),
                    "Rate-limit tests must use the nexus_quiz_test database");
        }
    }

    @AfterEach
    void cleanUpCreatedData() {
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
    }

    @Test
    void throttlesRepeatedUsernameButNotDifferentUsername() throws Exception {
        assertEquals(5, ReflectionTestUtils.getField(
                rateLimitingFilter, "usernameRequestsPerMinute"));
        assertTrue(filterChainProxy.getFilterChains().stream()
                .flatMap(chain -> chain.getFilters().stream())
                .anyMatch(filter -> filter == rateLimitingFilter));

        String repeatedUsername = uniqueUsername();
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(repeatedUsername)))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(repeatedUsername)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(uniqueUsername())))
                .andExpect(status().isUnauthorized());
    }

    private String uniqueUsername() {
        return "rate-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String loginBody(String username) throws Exception {
        return objectMapper.writeValueAsString(new LoginPayload(username, "NotARealPassword1"));
    }

    private record LoginPayload(String usernameOrEmail, String password) {
    }
}
