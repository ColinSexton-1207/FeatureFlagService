package com.example.flagship.flag.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FlagConfigurationRepository extends JpaRepository<FlagConfigurationEntity, UUID> {
    List<FlagConfigurationEntity> findByFlagId(UUID flagId);

    Optional<FlagConfigurationEntity> findByFlagIdAndEnvironmentId(UUID flagId, UUID environmentId);

    /** Ordered by the environment's defined rank, never by row insertion order. */
    @Query("""
            SELECT new com.example.flagship.flag.infrastructure.EnvironmentConfiguration(c, e.key)
            FROM FlagConfigurationEntity c
            JOIN EnvironmentEntity e ON e.id = c.environmentId
            WHERE c.flagId = :flagId
            ORDER BY e.sortOrder
            """)
    List<EnvironmentConfiguration> findWithEnvironmentByFlagIdOrderBySortOrder(UUID flagId);
}
