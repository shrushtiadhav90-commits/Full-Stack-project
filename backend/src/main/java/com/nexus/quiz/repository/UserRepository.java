package com.nexus.quiz.repository;

import com.nexus.quiz.entity.User;
import com.nexus.quiz.entity.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);

    boolean existsByRole(Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(u.username) = lower(:identifier) "
            + "or lower(u.email) = lower(:identifier)")
    Optional<User> findByUsernameOrEmailForUpdate(@Param("identifier") String identifier);
}
