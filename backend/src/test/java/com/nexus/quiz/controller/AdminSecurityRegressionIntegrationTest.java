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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminSecurityRegressionIntegrationTest {

    private static final String DUMMY_ID = "999999";
    private final List<User> testUsers = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    void createNonAdminPrincipals() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().contains("/nexus_quiz_test"),
                    "Admin security regression tests must use nexus_quiz_test");
        }
        for (Role role : List.of(Role.STUDENT, Role.PARENT, Role.FACULTY)) {
            String username = "security-" + role.name().toLowerCase() + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            testUsers.add(userRepository.saveAndFlush(User.builder()
                    .fullName("Security Test " + role)
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
    void deleteNonAdminPrincipals() {
        testUsers.forEach(user -> userRepository.deleteById(user.getId()));
        testUsers.clear();
    }

    @ParameterizedTest(name = "{0} {1} rejects unauthenticated and non-admin requests")
    @MethodSource("adminEndpoints")
    void everyAdminEndpointRequiresAdmin(
            HttpMethod method, String path, String body, boolean multipartRequest) throws Exception {
        assertSecurityEnvelope(perform(method, path, body, multipartRequest, null)
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString());

        for (User user : testUsers) {
            String token = jwtService.generateAccessToken(user);
            assertSecurityEnvelope(perform(method, path, body, multipartRequest, token)
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
            MockMultipartHttpServletRequestBuilder multipartBuilder = multipart(path);
            if (path.endsWith("/bulk-upload")) {
                multipartBuilder.file(new MockMultipartFile(
                        "file", "users.csv", "text/csv",
                        "fullName,username,email,phone\nA User,a.user,a@example.test,123\n"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                multipartBuilder.param("role", "PARENT");
            } else {
                multipartBuilder.file(new MockMultipartFile(
                        "file", "logo.png", "image/png", new byte[]{1, 2, 3}));
            }
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

    private void assertSecurityEnvelope(String responseBody) throws Exception {
        JsonNode response = objectMapper.readTree(responseBody);
        assertFalse(response.path("success").asBoolean());
        assertTrue(response.path("message").isTextual());
        assertNotNull(response.get("data"));
        assertTrue(response.get("data").isNull());
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> adminEndpoints() {
        return Stream.of(
                endpoint(HttpMethod.POST, "/api/v1/admin/users",
                        """
                        {"fullName":"Security User","username":"security-user","role":"PARENT"}
                        """),
                endpoint(HttpMethod.GET, "/api/v1/admin/users"),
                endpoint(HttpMethod.GET, "/api/v1/admin/users/" + DUMMY_ID),
                endpoint(HttpMethod.PUT, "/api/v1/admin/users/" + DUMMY_ID,
                        """
                        {"fullName":"Security User","username":"security-user"}
                        """),
                endpoint(HttpMethod.PATCH, "/api/v1/admin/users/" + DUMMY_ID + "/deactivate"),
                endpoint(HttpMethod.PATCH, "/api/v1/admin/users/" + DUMMY_ID + "/activate"),
                endpoint(HttpMethod.POST, "/api/v1/admin/users/" + DUMMY_ID + "/reset-password"),
                multipartEndpoint("/api/v1/admin/users/bulk-upload"),
                endpoint(HttpMethod.GET, "/api/v1/admin/users/template/PARENT"),
                endpoint(HttpMethod.POST, "/api/v1/admin/academic-years",
                        """
                        {"name":"2098-2099","startDate":"2098-06-01","endDate":"2099-04-30","current":false}
                        """),
                endpoint(HttpMethod.PUT, "/api/v1/admin/academic-years/" + DUMMY_ID,
                        """
                        {"name":"2098-2099","startDate":"2098-06-01","endDate":"2099-04-30","current":false}
                        """),
                endpoint(HttpMethod.GET, "/api/v1/admin/academic-years"),
                endpoint(HttpMethod.POST, "/api/v1/admin/classes",
                        """
                        {"name":"Class 5","section":"A","academicYearId":1}
                        """),
                endpoint(HttpMethod.PUT, "/api/v1/admin/classes/" + DUMMY_ID,
                        """
                        {"name":"Class 5","section":"A","academicYearId":1}
                        """),
                endpoint(HttpMethod.GET, "/api/v1/admin/classes"),
                endpoint(HttpMethod.DELETE, "/api/v1/admin/classes/" + DUMMY_ID),
                endpoint(HttpMethod.POST, "/api/v1/admin/subjects",
                        """
                        {"name":"Security Subject","code":"SEC"}
                        """),
                endpoint(HttpMethod.PUT, "/api/v1/admin/subjects/" + DUMMY_ID,
                        """
                        {"name":"Security Subject","code":"SEC"}
                        """),
                endpoint(HttpMethod.GET, "/api/v1/admin/subjects"),
                endpoint(HttpMethod.DELETE, "/api/v1/admin/subjects/" + DUMMY_ID),
                endpoint(HttpMethod.POST, "/api/v1/admin/faculty-assignments",
                        """
                        {"facultyUserId":1,"classId":1,"subjectId":1}
                        """),
                endpoint(HttpMethod.GET, "/api/v1/admin/faculty-assignments"),
                endpoint(HttpMethod.DELETE, "/api/v1/admin/faculty-assignments/" + DUMMY_ID),
                endpoint(HttpMethod.POST, "/api/v1/admin/parent-links",
                        """
                        {"parentUserId":1,"studentId":1,"relation":"GUARDIAN"}
                        """),
                endpoint(HttpMethod.GET, "/api/v1/admin/parent-links"),
                endpoint(HttpMethod.DELETE, "/api/v1/admin/parent-links/" + DUMMY_ID),
                endpoint(HttpMethod.GET, "/api/v1/admin/settings"),
                endpoint(HttpMethod.PUT, "/api/v1/admin/settings",
                        """
                        {"schoolName":"Security Test School","primaryColor":"#112233",
                         "secondaryColor":"#445566","leaderboardEnabled":false,
                         "emailNotificationsEnabled":false}
                        """),
                multipartEndpoint("/api/v1/admin/settings/logo"),
                endpoint(HttpMethod.GET, "/api/v1/admin/audit-log")
        );
    }

    private static org.junit.jupiter.params.provider.Arguments endpoint(HttpMethod method, String path) {
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
