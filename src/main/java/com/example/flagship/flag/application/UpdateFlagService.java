package com.example.flagship.flag.application;

import com.example.flagship.flag.infrastructure.FeatureFlagEntity;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateFlagService {
    private final FlagLookup lookup;
    private final Clock clock;

    UpdateFlagService(FlagLookup lookup, Clock clock) {
        this.lookup = lookup;
        this.clock = clock;
    }

    /** Applies a metadata patch. A patch that changes nothing writes nothing and keeps updatedAt. */
    @Transactional
    public FlagDetails update(String key, FlagMetadataPatch patch) {
        FeatureFlagEntity flag = lookup.flag(key);
        if (flag.isArchived()) { throw new FlagArchivedException(key); }

        String name = patch.name() != null ? patch.name() : flag.getName();
        String description = patch.setDescription() ? patch.description() : flag.getDescription();

        if (!name.equals(flag.getName()) || !Objects.equals(description, flag.getDescription())) {
            flag.updateMetadata(name, description, clock.instant());
        }
        
        return FlagDetails.from(flag);
    }
}
