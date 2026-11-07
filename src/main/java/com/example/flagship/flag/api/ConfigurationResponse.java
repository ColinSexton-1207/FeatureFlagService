package com.example.flagship.flag.api;

import com.example.flagship.flag.application.ConfigurationDetails;
import java.time.Instant;
import java.util.List;

public record ConfigurationResponse(
        String flagKey,
        String environment,
        boolean enabled,
        int rolloutPercentage,
        List<String> allowUsers,
        List<String> denyUsers,
        long version,
        Instant updatedAt,
        String updatedBy) {

    static ConfigurationResponse from(ConfigurationDetails d) {
        return new ConfigurationResponse(d.flagKey(), d.environment(), d.enabled(), d.rolloutPercentage(),
                d.allowUsers(), d.denyUsers(), d.version(), d.updatedAt(), d.updatedBy());
    }
}
