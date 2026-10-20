package com.nexus.quiz.service;

import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private static final long MAX_IMAGE_SIZE_BYTES = 2L * 1024 * 1024;
    private static final long MAX_CSV_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private final Path uploadRoot;
    private final Tika tika;

    public LocalFileStorageService(
            @Value("${app.upload-dir:${UPLOAD_DIR:uploads}}") String uploadDirectory) {
        this.uploadRoot = Paths.get(uploadDirectory).toAbsolutePath().normalize();
        this.tika = new Tika();
    }

    @Override
    public String store(MultipartFile file, String subfolder) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        Path directory = resolveSubfolder(subfolder);
        String detectedType;
        safeOriginalFilename(file);
        try (InputStream input = file.getInputStream()) {
            detectedType = tika.detect(input);
        }
        String extension = extensionFor(detectedType, file);
        long maxSize = extension.equals(".csv") ? MAX_CSV_SIZE_BYTES : MAX_IMAGE_SIZE_BYTES;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(extension.equals(".csv")
                    ? "CSV files must not exceed 5 MB"
                    : "Image files must not exceed 2 MB");
        }

        Files.createDirectories(directory);
        ensureInsideUploadRoot(directory);
        String storedFilename = UUID.randomUUID() + extension;
        Path destination = resolveFilename(directory, storedFilename);
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        return storedFilename;
    }

    @Override
    public Resource load(String subfolder, String filename) throws IOException {
        Path file = resolveFilename(resolveSubfolder(subfolder), filename);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Stored file was not found");
        }
        ensureInsideUploadRoot(file);
        Resource resource = new UrlResource(file.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new IOException("Stored file is not readable");
        }
        return resource;
    }

    @Override
    public void delete(String subfolder, String filename) throws IOException {
        Path directory = resolveSubfolder(subfolder);
        Path file = resolveFilename(directory, filename);
        if (Files.exists(file)) {
            ensureInsideUploadRoot(file);
        } else if (Files.exists(directory)) {
            ensureInsideUploadRoot(directory);
        }
        Files.deleteIfExists(file);
    }

    private Path resolveSubfolder(String subfolder) throws IOException {
        validatePathSegment(subfolder, "subfolder");
        Path directory = uploadRoot.resolve(subfolder).normalize();
        if (!directory.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid storage subfolder");
        }
        if (Files.exists(directory)) {
            ensureInsideUploadRoot(directory);
        }
        return directory;
    }

    private Path resolveFilename(Path directory, String filename) throws IOException {
        validatePathSegment(filename, "filename");
        Path file = directory.resolve(filename).normalize();
        if (!file.startsWith(uploadRoot) || !file.startsWith(directory)) {
            throw new IllegalArgumentException("Invalid file path");
        }
        return file;
    }

    private void validatePathSegment(String segment, String name) {
        if (segment == null || segment.isBlank()
                || ".".equals(segment)
                || segment.contains("..")
                || segment.contains("/")
                || segment.contains("\\")
                || segment.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid " + name);
        }
    }

    private void ensureInsideUploadRoot(Path path) throws IOException {
        Path realRoot = uploadRoot.toRealPath();
        Path realPath = path.toRealPath();
        if (!realPath.startsWith(realRoot)) {
            throw new IllegalArgumentException("Resolved path is outside the upload directory");
        }
    }

    private String extensionFor(String detectedType, MultipartFile file) throws IOException {
        if (IMAGE_EXTENSIONS.containsKey(detectedType)) {
            return IMAGE_EXTENSIONS.get(detectedType);
        }
        if (isCsvContent(detectedType, file)) {
            return ".csv";
        }
        throw new IllegalArgumentException("Unsupported file type");
    }

    private boolean isCsvContent(String detectedType, MultipartFile file) throws IOException {
        if (!"text/csv".equals(detectedType) && !"application/csv".equals(detectedType)
                && !"text/plain".equals(detectedType)) {
            return false;
        }
        if (file.getSize() > MAX_CSV_SIZE_BYTES) {
            throw new IllegalArgumentException("CSV files must not exceed 5 MB");
        }
        byte[] bytes = file.getBytes();
        String content = new String(bytes, StandardCharsets.UTF_8);
        return !content.isBlank()
                && content.indexOf(',') >= 0
                && content.indexOf('\0') < 0;
    }

    private String safeOriginalFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null
                || originalFilename.contains("..")
                || originalFilename.contains("/")
                || originalFilename.contains("\\")
                || originalFilename.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid original filename");
        }
        return originalFilename;
    }
}
