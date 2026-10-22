package com.nexus.quiz.service;

import com.nexus.quiz.entity.SchoolSettings;
import com.nexus.quiz.repository.SchoolSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Optional;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Mock
    private SchoolSettingsRepository settingsRepository;
    @Mock
    private AuditService auditService;

    private SchoolSettings settings;
    private SettingsService settingsService;

    @BeforeEach
    void setUp() {
        settings = SchoolSettings.builder()
                .id(1L)
                .schoolName("Test School")
                .primaryColor("#2563EB")
                .secondaryColor("#F59E0B")
                .leaderboardEnabled(false)
                .emailNotificationsEnabled(false)
                .build();
        when(settingsRepository.findById(1L)).thenReturn(Optional.of(settings));
        settingsService = new SettingsService(
                settingsRepository,
                new LocalFileStorageService(temporaryDirectory.toString()),
                auditService);
    }

    @Test
    void rejectsGifLogo() {
        MockMultipartFile gif = new MockMultipartFile(
                "file", "school.gif", "image/gif",
                "GIF89a".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        assertThrows(IllegalArgumentException.class, () -> settingsService.updateLogo(1L, gif));
        verify(settingsRepository, never()).save(any(SchoolSettings.class));
    }

    @Test
    void acceptsWebpLogo() throws Exception {
        byte[] webp = Base64.getDecoder().decode(
                "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v89WAAAAA==");
        when(settingsRepository.findById(1L)).thenReturn(Optional.of(settings));
        when(settingsRepository.save(any(SchoolSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = settingsService.updateLogo(1L,
                new MockMultipartFile("file", "school.webp", "image/webp", webp));

        assertEquals("/api/v1/files/logo/" + settings.getLogoUrl().substring(
                "/api/v1/files/logo/".length()), response.logoUrl());
        org.junit.jupiter.api.Assertions.assertTrue(response.logoUrl().endsWith(".webp"));
        verify(auditService).record(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("SCHOOL_LOGO_UPDATED"),
                org.mockito.ArgumentMatchers.eq("SCHOOL_SETTINGS"),
                org.mockito.ArgumentMatchers.eq(1L),
                any(),
                any());
    }

    @Test
    void rejectsLogoWithMismatchedContentAndRejectsSvg() {
        MockMultipartFile textNamedPng = new MockMultipartFile(
                "file", "school.png", "image/png", "not an image".getBytes());
        MockMultipartFile svg = new MockMultipartFile(
                "file", "school.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class,
                () -> settingsService.updateLogo(1L, textNamedPng));
        assertThrows(IllegalArgumentException.class,
                () -> settingsService.updateLogo(1L, svg));
        verify(settingsRepository, never()).save(any(SchoolSettings.class));
    }

    @Test
    void deletesOldLogoAfterNewLogoIsStored() throws Exception {
        LocalFileStorageService storage = new LocalFileStorageService(temporaryDirectory.toString());
        String oldFilename = storage.store(
                new MockMultipartFile("file", "old.png", "image/png", pngBytes()), "logos");
        settings.setLogoUrl("/api/v1/files/logo/" + oldFilename);
        when(settingsRepository.findById(1L)).thenReturn(Optional.of(settings));
        when(settingsRepository.save(any(SchoolSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        SettingsService service = new SettingsService(settingsRepository, storage, auditService);

        service.updateLogo(1L,
                new MockMultipartFile("file", "new.png", "image/png", pngBytes()));

        assertFalse(Files.exists(temporaryDirectory.resolve("logos").resolve(oldFilename)));
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
