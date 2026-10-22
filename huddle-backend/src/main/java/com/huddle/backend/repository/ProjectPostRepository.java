package com.huddle.backend.repository;

import com.huddle.backend.model.ProjectPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectPostRepository extends JpaRepository<ProjectPost, String> {

    List<ProjectPost> findByOpenToCollabTrue();

    List<ProjectPost> findByPosterId(String posterId);

    long countByPosterId(String posterId);

    /**
     * Case-insensitive search across title and briefDescription.
     * Skills are element-collection tables; joining them here produces duplicates and
     * makes the query fragile — skill matching is done in Java stream instead.
     */
    @Query("SELECT DISTINCT p FROM ProjectPost p " +
           "WHERE p.openToCollab = true " +
           "AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(p.briefDescription) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<ProjectPost> searchByQuery(@Param("q") String q);
}
