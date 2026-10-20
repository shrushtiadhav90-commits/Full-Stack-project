package com.nexus.quiz.controller;

import com.nexus.quiz.dto.ApiResponse;
import com.nexus.quiz.dto.admin.PublicSettingsResponse;
import com.nexus.quiz.service.SettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class PublicSettingsController {

    private final SettingsService settingsService;

    public PublicSettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PublicSettingsResponse>> getPublicSettings() {
        return ResponseEntity.ok(ApiResponse.success("Public school settings",
                settingsService.getPublicSettings()));
    }
}
