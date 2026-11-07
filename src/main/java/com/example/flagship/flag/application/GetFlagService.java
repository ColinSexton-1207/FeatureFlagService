package com.example.flagship.flag.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetFlagService {
    private final FlagLookup lookup;

    GetFlagService(FlagLookup lookup) {
        this.lookup = lookup;
    }

    /** Archived flags are returned too, with archivedAt set. */
    @Transactional(readOnly = true)
    public FlagDetails get(String key) {
        return FlagDetails.from(lookup.flag(key));
    }
}
