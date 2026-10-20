package com.nexus.quiz.repository;

import com.nexus.quiz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);
}
