package com.nexus.quiz.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorageService {

    String store(MultipartFile file, String subfolder) throws IOException;

    Resource load(String subfolder, String filename) throws IOException;

    void delete(String subfolder, String filename) throws IOException;
}
