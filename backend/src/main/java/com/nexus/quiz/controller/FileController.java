package com.nexus.quiz.controller;

import com.nexus.quiz.exception.ResourceNotFoundException;
import com.nexus.quiz.service.FileStorageService;
import org.apache.tika.Tika;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileStorageService fileStorageService;
    private final Tika tika = new Tika();

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/logo/{filename:.+}")
    public ResponseEntity<Resource> getLogo(@PathVariable String filename) throws IOException {
        return serve("logos", filename);
    }

    @GetMapping("/profile-photos/{filename:.+}")
    public ResponseEntity<Resource> getProfilePhoto(@PathVariable String filename) throws IOException {
        return serve("profile-photos", filename);
    }

    private ResponseEntity<Resource> serve(String subfolder, String filename) throws IOException {
        validateFilename(filename);
        Resource resource;
        try {
            resource = fileStorageService.load(subfolder, filename);
        } catch (IOException exception) {
            throw new ResourceNotFoundException("File not found");
        }
        String detectedType;
        try (InputStream input = resource.getInputStream()) {
            detectedType = tika.detect(input);
        }
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(detectedType);
        } catch (IllegalArgumentException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }

    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.contains("..")
                || filename.contains("/") || filename.contains("\\") || filename.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid filename");
        }
    }
}
