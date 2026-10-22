package com.huddle.backend.repository;

import com.huddle.backend.model.InstitutionStatus;
import com.huddle.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByGoogleSub(String googleSub);
    boolean existsByGoogleSub(String googleSub);
    List<User> findByInstitutionStatus(InstitutionStatus status);
}
