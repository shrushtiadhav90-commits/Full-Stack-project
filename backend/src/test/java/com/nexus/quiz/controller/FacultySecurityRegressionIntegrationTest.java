package com.nexus.quiz.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.quiz.entity.Role;
import com.nexus.quiz.entity.User;
import com.nexus.quiz.repository.UserRepository;
import com.nexus.quiz.security.JwtService;
import com.nexus.quiz.util.PasswordUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FacultySecurityRegressionIntegrationTest {

    private static final String DUMMY_ID = "999999";
    private static final String QUESTION = """
            {"text":"Question","type":"SHORT_ANSWER","subjectId":1,"classLevel":"Class 6",
             "difficulty":"MEDIUM","marks":1,"options":[]}
            """;
    private static final String QUIZ = """
            {"title":"Quiz","subjectId":1,"classIds":[1],"questions":[],"durationMinutes":20,
             "startTime":"2030-10-24T10:00:00+05:30","endTime":"2030-10-24T11:00:00+05:30",
             "maxAttempts":1,"shuffleQuestions":false,"shuffleOptions":false,
             "showAnswers":"AFTER_END","passPercentage":40}
            """;
    private static final String GRADE = """
            {"marksAwarded":0,"feedback":"Reviewed"}
            """;
    private final List<User> users = new ArrayList<>();

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DataSource dataSource;

    @BeforeAll
    void createNonFacultyUsers() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().contains("/nexus_quiz_test"),
                    "Faculty security tests must use nexus_quiz_test");
        }
        for (Role role : List.of(Role.STUDENT, Role.PARENT, Role.ADMIN)) {
            String username = "faculty-security-" + role.name().toLowerCase() + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            users.add(userRepository.saveAndFlush(User.builder()
                    .fullName("Faculty Security " + role)
                    .username(username)
                    .email(username + "@example.test")
                    .passwordHash(PasswordUtil.hash("TestPassword1"))
                    .role(role)
                    .isActive(true)
                    .mustChangePassword(false)
                    .failedLoginCount(0)
                    .build()));
        }
    }

    @AfterAll
    void deleteNonFacultyUsers() {
        users.forEach(user -> userRepository.deleteById(user.getId()));
        users.clear();
    }

    @ParameterizedTest(name = "{0} {1} rejects unauthenticated and non-faculty requests")
    @MethodSource("facultyEndpoints")
    void everyFacultyEndpointRequiresFaculty(
            HttpMethod method, String path, String body, boolean multipartRequest) throws Exception {
        assertEnvelope(perform(method, path, body, multipartRequest, null)
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString());
        for (User user : users) {
            String token = jwtService.generateAccessToken(user);
            assertEnvelope(perform(method, path, body, multipartRequest, token)
                    .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString());
        }
    }

    private ResultActions perform(
            HttpMethod method,
            String path,
            String body,
            boolean multipartRequest,
            String token) throws Exception {
        MockHttpServletRequestBuilder builder;
        if (multipartRequest) {
            MockMultipartHttpServletRequestBuilder multipartBuilder = multipart(path)
                    .file(new MockMultipartFile("image", "question.png", "image/png", new byte[]{1, 2, 3}));
            builder = multipartBuilder;
        } else {
            builder = request(method, path);
            if (!body.isBlank()) {
                builder.contentType(MediaType.APPLICATION_JSON).content(body);
            }
        }
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(builder);
    }

    private void assertEnvelope(String responseBody) throws Exception {
        JsonNode response = objectMapper.readTree(responseBody);
        assertFalse(response.path("success").asBoolean());
        assertTrue(response.path("message").isTextual());
        assertTrue(response.has("data"));
        assertTrue(response.get("data").isNull());
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> facultyEndpoints() {
        return Stream.of(
                endpoint(HttpMethod.GET, "/api/v1/faculty/assignments"),
                endpoint(HttpMethod.GET, "/api/v1/faculty/dashboard"),
                endpoint(HttpMethod.POST, "/api/v1/faculty/questions", QUESTION),
                endpoint(HttpMethod.GET, "/api/v1/faculty/questions"),
                endpoint(HttpMethod.GET, "/api/v1/faculty/questions/" + DUMMY_ID),
                endpoint(HttpMethod.PUT, "/api/v1/faculty/questions/" + DUMMY_ID, QUESTION),
                endpoint(HttpMethod.DELETE, "/api/v1/faculty/questions/" + DUMMY_ID),
                multipartEndpoint("/api/v1/faculty/questions/" + DUMMY_ID + "/image"),
                endpoint(HttpMethod.DELETE, "/api/v1/faculty/questions/" + DUMMY_ID + "/image"),
                endpoint(HttpMethod.POST, "/api/v1/faculty/quizzes", QUIZ),
                endpoint(HttpMethod.GET, "/api/v1/faculty/quizzes"),
                endpoint(HttpMethod.GET, "/api/v1/faculty/quizzes/" + DUMMY_ID),
                endpoint(HttpMethod.PUT, "/api/v1/faculty/quizzes/" + DUMMY_ID, QUIZ),
                endpoint(HttpMethod.DELETE, "/api/v1/faculty/quizzes/" + DUMMY_ID),
                endpoint(HttpMethod.POST, "/api/v1/faculty/quizzes/" + DUMMY_ID + "/publish"),
                endpoint(HttpMethod.POST, "/api/v1/faculty/quizzes/" + DUMMY_ID + "/close"),
                endpoint(HttpMethod.POST, "/api/v1/faculty/quizzes/" + DUMMY_ID + "/duplicate"),
                endpoint(HttpMethod.GET, "/api/v1/faculty/grading"),
                endpoint(HttpMethod.PUT, "/api/v1/faculty/grading/answers/" + DUMMY_ID, GRADE),
                endpoint(HttpMethod.GET, "/api/v1/faculty/quizzes/" + DUMMY_ID + "/report"));
    }

    private static org.junit.jupiter.params.provider.Arguments endpoint(
            HttpMethod method, String path) {
        return endpoint(method, path, "");
    }

    private static org.junit.jupiter.params.provider.Arguments endpoint(
            HttpMethod method, String path, String body) {
        return org.junit.jupiter.params.provider.Arguments.of(method, path, body, false);
    }

    private static org.junit.jupiter.params.provider.Arguments multipartEndpoint(String path) {
        return org.junit.jupiter.params.provider.Arguments.of(HttpMethod.POST, path, "", true);
    }
}
