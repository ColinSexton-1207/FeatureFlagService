package com.example.flagship.flag.application;

import com.example.flagship.environment.infrastructure.EnvironmentEntity;
import com.example.flagship.environment.infrastructure.EnvironmentRepository;
import com.example.flagship.flag.infrastructure.FeatureFlagEntity;
import com.example.flagship.flag.infrastructure.FeatureFlagRepository;
import org.springframework.stereotype.Component;

/** Resolves keys from the URL to entities in the default project, or throws the matching 404. */
@Component
class FlagLookup {
    private final DefaultProjectResolver projectResolver;
    private final FeatureFlagRepository flags;
    private final EnvironmentRepository environments;

    FlagLookup(DefaultProjectResolver projectResolver, FeatureFlagRepository flags,
               EnvironmentRepository environments) {
        this.projectResolver = projectResolver;
        this.flags = flags;
        this.environments = environments;
    }

    FeatureFlagEntity flag(String key) {
        return flags.findByProjectIdAndKey(projectResolver.defaultProjectId(), key)
                .orElseThrow(() -> new FlagNotFoundException(key));
    }

    EnvironmentEntity environment(FeatureFlagEntity flag, String environmentKey) {
        return environments.findByProjectIdAndKey(flag.getProjectId(), environmentKey)
                .orElseThrow(() -> new EnvironmentNotFoundException(environmentKey));
    }
}
