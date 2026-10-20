package com.nexus.quiz.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileStorageServiceTest {

    @TempDir
    Path temporaryDirectory;

    private LocalFileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalFileStorageService(temporaryDirectory.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        try (var files = java.nio.file.Files.walk(temporaryDirectory)) {
            for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                java.nio.file.Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void acceptsAndStoresValidPng() throws IOException {
        String storedName = storageService.store(
                new MockMultipartFile("file", "tiny.png", "image/png", pngBytes()),
                "questions");

        assertTrue(storageService.load("questions", storedName).exists());
    }

    @Test
    void rejectsTextFileRenamedToPng() {
        MockMultipartFile textNamedPng = new MockMultipartFile(
                "file", "notes.png", "image/png", "this is plain text".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> storageService.store(textNamedPng, "questions"));
    }

    @Test
    void rejectsImageLargerThanTwoMegabytes() throws IOException {
        byte[] oversizedPng = new byte[2 * 1024 * 1024 + 1];
        byte[] pngHeader = pngBytes();
        System.arraycopy(pngHeader, 0, oversizedPng, 0,
                Math.min(pngHeader.length, oversizedPng.length));
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "oversized.png", "image/png", oversizedPng);

        assertThrows(IllegalArgumentException.class,
                () -> storageService.store(oversized, "questions"));
    }

    @Test
    void rejectsPathTraversalForSubfolderAndFilename() throws IOException {
        assertThrows(IllegalArgumentException.class,
                () -> storageService.store(
                        new MockMultipartFile("file", "tiny.png", "image/png", pngBytes()),
                        "../outside"));
        assertThrows(IllegalArgumentException.class,
                () -> storageService.load("questions", "..\\outside.png"));
        assertThrows(IllegalArgumentException.class,
                () -> storageService.delete("questions", "nested/file.png"));
    }

    @Test
    void usesUuidForStoredFilenameInsteadOfOriginalName() throws IOException {
        String storedName = storageService.store(
                new MockMultipartFile("file", "student-profile.png", "image/png", pngBytes()),
                "profile-photos");

        assertTrue(storedName.endsWith(".png"));
        String uuidPart = storedName.substring(0, storedName.length() - ".png".length());
        assertDoesNotThrow(() -> UUID.fromString(uuidPart));
        assertTrue(!storedName.equals("student-profile.png"));
    }

    private byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
