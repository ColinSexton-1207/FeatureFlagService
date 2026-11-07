package com.example.flagship.flag.application;

import com.example.flagship.environment.infrastructure.EnvironmentEntity;
import com.example.flagship.flag.domain.FlagConfigurationRules;
import com.example.flagship.flag.infrastructure.FeatureFlagEntity;
import com.example.flagship.flag.infrastructure.FlagConfigurationEntity;
import com.example.flagship.flag.infrastructure.FlagConfigurationRepository;
import java.time.Clock;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateFlagConfigurationService {
    private final FlagLookup lookup;
    private final FlagConfigurationRepository configurations;
    private final Clock clock;

    UpdateFlagConfigurationService(FlagLookup lookup, FlagConfigurationRepository configurations, Clock clock) {
        this.lookup = lookup;
        this.configurations = configurations;
        this.clock = clock;
    }

    /**
     * Replaces one environment's configuration. Rules are validated before anything is read or
     * written. A request identical to the stored state writes nothing, so version, updatedAt and
     * updatedBy stay as they were; this comparison is explicit rather than left to Hibernate's
     * dirty checking.
     */
    @Transactional
    public ConfigurationDetails update(String flagKey, String environmentKey, UpdateConfigurationCommand command) {
        FlagConfigurationRules requested = command.toRules();

        FeatureFlagEntity flag = lookup.flag(flagKey);
        EnvironmentEntity environment = lookup.environment(flag, environmentKey);

        if (flag.isArchived()) { throw new FlagArchivedException(flagKey); }

        FlagConfigurationEntity configuration = configurations
                .findByFlagIdAndEnvironmentId(flag.getId(), environment.getId())
                .orElseThrow(() -> FlagConfigurationQueryService.missingRow(flagKey, environmentKey));

        if (configuration.rules().equals(requested)) {
            return ConfigurationDetails.from(flag.getKey(), environment.getKey(), configuration);
        }

        configuration.apply(requested, CreateFlagService.SYSTEM_ACTOR, clock.instant());
        try {
            // Flush inside this method so a lost race surfaces here, as a domain error, rather
            // than at commit time after the method has returned.
            configuration = configurations.saveAndFlush(configuration);
        } catch (OptimisticLockingFailureException e) {
            throw new ConfigurationVersionConflictException(flagKey, environmentKey, e);
        }
        
        return ConfigurationDetails.from(flag.getKey(), environment.getKey(), configuration);
    }
}
