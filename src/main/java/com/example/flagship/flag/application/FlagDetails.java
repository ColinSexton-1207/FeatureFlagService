package com.example.flagship.flag.application;

import com.example.flagship.flag.domain.FlagType;
import com.example.flagship.flag.infrastructure.FeatureFlagEntity;
import java.time.Instant;
import java.util.UUID;

/** Read model returned by flag use cases, so JPA entities never leave the application layer. */
public record FlagDetails(
        UUID id,
        String key,
        String name,
        String description,
        FlagType flagType,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt) {

    static FlagDetails from(FeatureFlagEntity e) {
        return new FlagDetails(e.getId(), e.getKey(), e.getName(), e.getDescription(), e.getFlagType(),
                e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt(), e.getArchivedAt());
    }
}
