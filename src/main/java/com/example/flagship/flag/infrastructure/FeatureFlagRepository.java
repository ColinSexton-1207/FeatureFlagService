package com.example.flagship.flag.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlagEntity, UUID> {

    Optional<FeatureFlagEntity> findByProjectIdAndKey(UUID projectId, String key);

    boolean existsByProjectIdAndKey(UUID projectId, String key);
}
