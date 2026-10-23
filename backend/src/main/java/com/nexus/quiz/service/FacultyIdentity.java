package com.nexus.quiz.service;

import com.nexus.quiz.entity.Role;
import com.nexus.quiz.entity.User;
import com.nexus.quiz.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class FacultyIdentity {

    private final UserRepository userRepository;

    public FacultyIdentity(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Long requireFacultyId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Number principalId)) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated faculty user is required");
        }
        User faculty = userRepository.findById(principalId.longValue())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "Authenticated faculty user was not found"));
        if (faculty.getRole() != Role.FACULTY) {
            throw new AccessDeniedException("Faculty access is required");
        }
        return faculty.getId();
    }
}
