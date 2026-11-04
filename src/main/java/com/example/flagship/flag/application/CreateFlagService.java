package com.example.flagship.flag.application;

import com.example.flagship.environment.infrastructure.EnvironmentEntity;
import com.example.flagship.environment.infrastructure.EnvironmentRepository;
import com.example.flagship.flag.domain.FlagType;
import com.example.flagship.flag.infrastructure.FeatureFlagEntity;
import com.example.flagship.flag.infrastructure.FeatureFlagRepository;
import com.example.flagship.flag.infrastructure.FlagConfigurationEntity;
import com.example.flagship.flag.infrastructure.FlagConfigurationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateFlagService {

    /** Placeholder actor until the security slice supplies the authenticated principal. */
    static final String SYSTEM_ACTOR = "system";

    private final DefaultProjectResolver projectResolver;
    private final FeatureFlagRepository flags;
    private final FlagConfigurationRepository configurations;
    private final EnvironmentRepository environments;
    private final Clock clock;

    CreateFlagService(DefaultProjectResolver projectResolver, FeatureFlagRepository flags,
                      FlagConfigurationRepository configurations, EnvironmentRepository environments,
                      Clock clock) {
        this.projectResolver = projectResolver;
        this.flags = flags;
        this.configurations = configurations;
        this.environments = environments;
        this.clock = clock;
    }

    /**
     * Creates the flag and one disabled, 0% configuration per environment in a single transaction:
     * either the flag exists with all of its configurations, or nothing was written.
     */
    @Transactional
    public FlagDetails create(CreateFlagCommand command) {
        UUID projectId = projectResolver.defaultProjectId();

        // Fast path for the common duplicate case; the unique constraint below still decides races.
        if (flags.existsByProjectIdAndKey(projectId, command.key())) {
            throw new DuplicateFlagKeyException(command.key(), null);
        }

        Instant now = clock.instant();
        FlagType type = command.flagType() == null ? FlagType.BOOLEAN : command.flagType();
        FeatureFlagEntity flag = new FeatureFlagEntity(
                projectId, command.key(), command.name(), command.description(), type, SYSTEM_ACTOR, now);

        try {
            // Flush now so a concurrent duplicate surfaces here as a constraint violation,
            // not later at commit time where it can't be mapped to a clear 409.
            flag = flags.saveAndFlush(flag);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateFlagKeyException(command.key(), e);
        }

        UUID flagId = flag.getId();
        List<FlagConfigurationEntity> initial = environments.findByProjectIdOrderBySortOrder(projectId).stream()
                .map(EnvironmentEntity::getId)
                .map(envId -> FlagConfigurationEntity.initial(flagId, envId, SYSTEM_ACTOR, now))
                .toList();
        configurations.saveAll(initial);

        return FlagDetails.from(flag);
    }
}
