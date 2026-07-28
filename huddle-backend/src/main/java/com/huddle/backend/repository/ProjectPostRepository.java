package com.huddle.backend.repository;

import com.huddle.backend.model.ProjectPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectPostRepository extends JpaRepository<ProjectPost, String> {
    List<ProjectPost> findByOpenToCollabTrue();
}
