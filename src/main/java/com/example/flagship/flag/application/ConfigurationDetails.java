package com.example.flagship.flag.application;

import com.example.flagship.flag.infrastructure.FlagConfigurationEntity;
import java.time.Instant;
import java.util.List;

/** Read model for one environment's configuration. */
public record ConfigurationDetails(
        String flagKey,
        String environment,
        boolean enabled,
        int rolloutPercentage,
        List<String> allowUsers,
        List<String> denyUsers,
        long version,
        Instant updatedAt,
        String updatedBy) {

    static ConfigurationDetails from(String flagKey, String environmentKey, FlagConfigurationEntity c) {
        return new ConfigurationDetails(flagKey, environmentKey, c.isEnabled(), c.getRolloutPercentage(),
                c.getAllowUsers(), c.getDenyUsers(), c.getVersion(), c.getUpdatedAt(), c.getUpdatedBy());
    }
}
