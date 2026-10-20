package com.nexus.quiz.dto;

import java.util.Map;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Map<String, Object> errors) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static ApiResponse<Void> failure(String message, Map<String, Object> errors) {
        return new ApiResponse<>(false, message, null, errors);
    }
}
