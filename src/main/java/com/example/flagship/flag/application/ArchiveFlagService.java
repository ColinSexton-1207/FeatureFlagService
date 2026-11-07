package com.example.flagship.flag.application;

import com.example.flagship.flag.infrastructure.FeatureFlagEntity;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArchiveFlagService {
    private final FlagLookup lookup;
    private final Clock clock;

    ArchiveFlagService(FlagLookup lookup, Clock clock) {
        this.lookup = lookup;
        this.clock = clock;
    }

    /**
     * Archives a flag; never hard-deletes. Its environment configurations are left exactly as they
     * were. Archiving an already-archived flag is a no-op that keeps the original archive time.
     */
    @Transactional
    public void archive(String key) {
        FeatureFlagEntity flag = lookup.flag(key);
        if (!flag.isArchived()) { flag.archive(clock.instant()); }
    }
}
