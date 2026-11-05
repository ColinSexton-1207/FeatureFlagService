package com.example.flagship.flag.application;

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

    @Transactional
    public FlagDetails create(CreateFlagCommand command) {
        UUID projectId = projectResolver.defaultProjectId();

        if (flags.existsByProjectIdAndKey(projectId, command.key())) {
            throw new DuplicateFlagKeyException(command.key(), null);
        }

        Instant now = clock.instant();
        FlagType type = command.flagType() == null ? FlagType.BOOLEAN : command.flagType();
        FeatureFlagEntity flag = new FeatureFlagEntity(
                projectId, command.key(), command.name(), command.description(), type, SYSTEM_ACTOR, now);

        try {
            flag = flags.saveAndFlush(flag);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateFlagKeyException(command.key(), e);
        }

        UUID flagId = flag.getId();
        List<FlagConfigurationEntity> initial = environments.findByProjectIdOrderBySortOrder(projectId).stream()
                .map(environment -> environment.getId())
                .map(envId -> FlagConfigurationEntity.initial(flagId, envId, SYSTEM_ACTOR, now))
                .toList();
        configurations.saveAll(initial);

        return FlagDetails.from(flag);
    }
}
