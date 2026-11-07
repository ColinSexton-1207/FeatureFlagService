package com.example.flagship.flag.application;

/** Which lifecycle "tab" a flag listing shows. */
public enum FlagStatusFilter {
    ACTIVE, ARCHIVED, ALL;

    boolean includesActive() { return this != ARCHIVED; }

    boolean includesArchived() { return this != ACTIVE; }
}
