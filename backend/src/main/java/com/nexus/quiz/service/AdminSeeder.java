package com.nexus.quiz.service;

import com.nexus.quiz.entity.Role;
import com.nexus.quiz.entity.User;
import com.nexus.quiz.repository.UserRepository;
import com.nexus.quiz.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final String defaultUsername;
    private final String defaultPassword;

    public AdminSeeder(
            UserRepository userRepository,
            @Value("${app.admin.default-username:}") String defaultUsername,
            @Value("${app.admin.default-password:}") String defaultPassword) {
        this.userRepository = userRepository;
        this.defaultUsername = defaultUsername;
        this.defaultPassword = defaultPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (defaultUsername.isBlank() || defaultPassword.isBlank()) {
            throw new IllegalStateException(
                    "ADMIN_DEFAULT_USERNAME and ADMIN_DEFAULT_PASSWORD must be configured when no admin exists");
        }

        User admin = User.builder()
                .fullName(defaultUsername)
                .username(defaultUsername)
                .passwordHash(PasswordUtil.hash(defaultPassword))
                .role(Role.ADMIN)
                .isActive(true)
                .mustChangePassword(true)
                .failedLoginCount(0)
                .build();
        userRepository.save(admin);
        LOGGER.info("Default admin created");
    }
}
