package com.example.flagship.flag.application;

import com.example.flagship.environment.infrastructure.EnvironmentEntity;
import com.example.flagship.flag.infrastructure.FeatureFlagEntity;
import com.example.flagship.flag.infrastructure.FlagConfigurationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlagConfigurationQueryService {
    private final FlagLookup lookup;
    private final FlagConfigurationRepository configurations;

    FlagConfigurationQueryService(FlagLookup lookup, FlagConfigurationRepository configurations) {
        this.lookup = lookup;
        this.configurations = configurations;
    }

    /** All environments, in the environments' defined order. Archived flags remain readable. */
    @Transactional(readOnly = true)
    public List<ConfigurationDetails> list(String flagKey) {
        FeatureFlagEntity flag = lookup.flag(flagKey);
        
        return configurations.findWithEnvironmentByFlagIdOrderBySortOrder(flag.getId()).stream()
                .map(row -> ConfigurationDetails.from(flag.getKey(), row.environmentKey(), row.configuration()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConfigurationDetails get(String flagKey, String environmentKey) {
        FeatureFlagEntity flag = lookup.flag(flagKey);
        EnvironmentEntity environment = lookup.environment(flag, environmentKey);

        return configurations.findByFlagIdAndEnvironmentId(flag.getId(), environment.getId())
                .map(c -> ConfigurationDetails.from(flag.getKey(), environment.getKey(), c))
                .orElseThrow(() -> missingRow(flagKey, environmentKey));
    }

    static IllegalStateException missingRow(String flagKey, String environmentKey) {
        // Every flag gets a row per environment at creation, so this is a data-integrity failure (500).
        return new IllegalStateException(
                "Configuration row missing for flag '" + flagKey + "' in environment '" + environmentKey + "'");
    }
}
