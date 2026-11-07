package com.example.flagship.flag.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlagEntity, UUID> {

    Optional<FeatureFlagEntity> findByProjectIdAndKey(UUID projectId, String key);

    boolean existsByProjectIdAndKey(UUID projectId, String key);

    /** {@code pattern} must already be lower-cased and LIKE-escaped with a backslash. */
    @Query("""
            SELECT f FROM FeatureFlagEntity f
            WHERE f.projectId = :projectId
              AND ((:includeActive = TRUE AND f.archivedAt IS NULL)
                   OR (:includeArchived = TRUE AND f.archivedAt IS NOT NULL))
              AND (LOWER(f.key) LIKE :pattern ESCAPE '\\' OR LOWER(f.name) LIKE :pattern ESCAPE '\\')
            """)
    Page<FeatureFlagEntity> search(UUID projectId, boolean includeActive, boolean includeArchived,
                                   String pattern, Pageable pageable);
}
