package com.nexus.quiz.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.quiz.entity.AuditLog;
import com.nexus.quiz.entity.User;
import com.nexus.quiz.repository.AuditLogRepository;
import com.nexus.quiz.repository.UserRepository;
import com.nexus.quiz.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditLogQueryIntegrationTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private final List<Long> ownedAuditIds = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    private User admin;
    private String actionType;

    @BeforeEach
    void verifyTestDatabaseAndLoadAdmin() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().contains("/nexus_quiz_test"),
                    "Audit log tests must use nexus_quiz_test");
        }
        admin = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(
                "test-seed-admin", "test-seed-admin").orElseThrow();
        actionType = "DATE_FILTER_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @AfterEach
    void removeOwnedAuditRows() {
        auditLogRepository.deleteAllById(ownedAuditIds);
        ownedAuditIds.clear();
    }

    @Test
    void dateFiltersUseIstBoundariesAndResultsAreNewestFirst() throws Exception {
        LocalDate date = LocalDate.of(2025, 1, 15);
        Instant beforeDate = date.minusDays(1).atTime(23, 59, 59).atZone(IST).toInstant();
        Instant startOfDate = date.atStartOfDay(IST).toInstant();
        Instant withinDate = date.atTime(12, 0).atZone(IST).toInstant();
        Instant endOfDate = date.plusDays(1).atStartOfDay(IST).toInstant().minusNanos(1_000);
        Instant afterDate = date.plusDays(1).atStartOfDay(IST).toInstant();

        saveAudit(beforeDate);
        AuditLog firstIncluded = saveAudit(startOfDate);
        AuditLog secondIncluded = saveAudit(withinDate);
        AuditLog thirdIncluded = saveAudit(endOfDate);
        saveAudit(afterDate);

        String responseBody = mockMvc.perform(get("/api/v1/admin/audit-log")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateAccessToken(admin))
                        .param("actionType", actionType)
                        .param("from", date.toString())
                        .param("to", date.toString())
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode entries = objectMapper.readTree(responseBody).path("data").path("content");
        assertEquals(3, entries.size(), responseBody);
        assertEquals(thirdIncluded.getId(), entries.get(0).path("id").asLong());
        assertEquals(secondIncluded.getId(), entries.get(1).path("id").asLong());
        assertEquals(firstIncluded.getId(), entries.get(2).path("id").asLong());
        for (JsonNode entry : entries) {
            assertEquals(admin.getId(), entry.path("adminUserId").asLong());
            assertEquals(ZoneOffset.ofHoursMinutes(5, 30),
                    OffsetDateTime.parse(entry.path("createdAt").asText()).getOffset());
        }
    }

    private AuditLog saveAudit(Instant createdAtUtc) {
        AuditLog saved = auditLogRepository.saveAndFlush(AuditLog.builder()
                .adminUserId(admin.getId())
                .actionType(actionType)
                .targetEntityType("TEST")
                .targetEntityId(null)
                .oldValue(null)
                .newValue("{\"testMarker\":\"" + actionType + "\"}")
                .createdAtUtc(createdAtUtc)
                .build());
        ownedAuditIds.add(saved.getId());
        return saved;
    }
}
