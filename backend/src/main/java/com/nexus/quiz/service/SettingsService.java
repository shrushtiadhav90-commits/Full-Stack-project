package com.nexus.quiz.service;

import com.nexus.quiz.dto.admin.PublicSettingsResponse;
import com.nexus.quiz.dto.admin.SettingsResponse;
import com.nexus.quiz.dto.admin.UpdateSettingsRequest;
import com.nexus.quiz.entity.SchoolSettings;
import com.nexus.quiz.repository.SchoolSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class SettingsService {

    private static final String LOGO_SUBFOLDER = "logos";
    private static final String LOGO_URL_PREFIX = "/api/v1/files/logo/";
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final SchoolSettingsRepository settingsRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    public SettingsService(
            SchoolSettingsRepository settingsRepository,
            FileStorageService fileStorageService,
            AuditService auditService) {
        this.settingsRepository = settingsRepository;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
    }

    @Transactional
    public SettingsResponse getSettings() {
        return toResponse(settingsRepository.findById(1L).orElseGet(this::createDefault));
    }

    @Transactional
    public PublicSettingsResponse getPublicSettings() {
        SchoolSettings settings = settingsRepository.findById(1L).orElseGet(this::createDefault);
        return new PublicSettingsResponse(settings.getSchoolName(), settings.getLogoUrl(),
                settings.getPrimaryColor(), settings.getSecondaryColor());
    }

    @Transactional
    public SettingsResponse updateSettings(Long adminUserId, UpdateSettingsRequest request) {
        SchoolSettings settings = settingsRepository.findById(1L).orElseGet(this::createDefault);
        Map<String, Object> oldValue = auditValue(settings);
        settings.setSchoolName(request.schoolName().trim());
        settings.setPrimaryColor(request.primaryColor());
        settings.setSecondaryColor(request.secondaryColor());
        settings.setLeaderboardEnabled(request.leaderboardEnabled());
        settings.setEmailNotificationsEnabled(request.emailNotificationsEnabled());
        settings = settingsRepository.save(settings);
        auditService.record(adminUserId, "SCHOOL_SETTINGS_UPDATED", "SCHOOL_SETTINGS", 1L,
                oldValue, auditValue(settings));
        return toResponse(settings);
    }

    @Transactional
    public SettingsResponse updateLogo(Long adminUserId, MultipartFile file) throws IOException {
        SchoolSettings settings = settingsRepository.findById(1L).orElseGet(this::createDefault);
        String oldLogoUrl = settings.getLogoUrl();
        String filename = fileStorageService.store(file, LOGO_SUBFOLDER);
        settings.setLogoUrl(LOGO_URL_PREFIX + filename);
        settings = settingsRepository.save(settings);
        auditService.record(adminUserId, "SCHOOL_LOGO_UPDATED", "SCHOOL_SETTINGS", 1L,
                oldLogoUrl == null ? null : Map.of("logoUrl", oldLogoUrl),
                Map.of("logoUrl", settings.getLogoUrl()));
        if (oldLogoUrl != null && oldLogoUrl.startsWith(LOGO_URL_PREFIX)) {
            fileStorageService.delete(LOGO_SUBFOLDER,
                    oldLogoUrl.substring(LOGO_URL_PREFIX.length()));
        }
        return toResponse(settings);
    }

    private SchoolSettings createDefault() {
        return settingsRepository.save(defaultSettings());
    }

    private SchoolSettings defaultSettings() {
        return SchoolSettings.builder()
                .id(1L)
                .schoolName("Nexus Ranaji English Medium School")
                .primaryColor("#2563EB")
                .secondaryColor("#F59E0B")
                .leaderboardEnabled(true)
                .emailNotificationsEnabled(false)
                .build();
    }

    private Map<String, Object> auditValue(SchoolSettings settings) {
        return Map.of(
                "schoolName", settings.getSchoolName(),
                "logoUrl", settings.getLogoUrl() == null ? "" : settings.getLogoUrl(),
                "primaryColor", settings.getPrimaryColor(),
                "secondaryColor", settings.getSecondaryColor(),
                "leaderboardEnabled", settings.getLeaderboardEnabled(),
                "emailNotificationsEnabled", settings.getEmailNotificationsEnabled());
    }

    private SettingsResponse toResponse(SchoolSettings settings) {
        String updatedAt = settings.getUpdatedAtUtc() == null ? null
                : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(settings.getUpdatedAtUtc().atZone(IST));
        return new SettingsResponse(settings.getSchoolName(), settings.getLogoUrl(),
                settings.getPrimaryColor(), settings.getSecondaryColor(),
                Boolean.TRUE.equals(settings.getLeaderboardEnabled()),
                Boolean.TRUE.equals(settings.getEmailNotificationsEnabled()), updatedAt);
    }
}
