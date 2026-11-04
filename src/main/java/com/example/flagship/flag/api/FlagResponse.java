package com.example.flagship.flag.api;

import com.example.flagship.flag.application.FlagDetails;
import com.example.flagship.flag.domain.FlagType;
import java.time.Instant;
import java.util.UUID;

public record FlagResponse(
        UUID id,
        String key,
        String name,
        String description,
        FlagType flagType,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt) {

    static FlagResponse from(FlagDetails d) {
        return new FlagResponse(d.id(), d.key(), d.name(), d.description(), d.flagType(),
                d.createdBy(), d.createdAt(), d.updatedAt(), d.archivedAt());
    }
}
