package com.example.flagship.flag.application;

import com.example.flagship.flag.infrastructure.FeatureFlagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetFlagService {
    private final DefaultProjectResolver projectResolver;
    private final FeatureFlagRepository flags;

    GetFlagService(DefaultProjectResolver projectResolver, FeatureFlagRepository flags) {
        this.projectResolver = projectResolver;
        this.flags = flags;
    }

   @Transactional(readOnly = true)
    public FlagDetails get(String key) {
        return flags.findByProjectIdAndKey(projectResolver.defaultProjectId(), key)
                .map(FlagDetails::from)
                .orElseThrow(() -> new FlagNotFoundException(key));
    }
}
