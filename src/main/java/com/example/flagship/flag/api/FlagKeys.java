package com.example.flagship.flag.api;

/**
 * Flag keys appear in URLs, cache keys and rollout hash input (flagKey:userId), so they are
 * restricted to a URL-safe, colon-free lowercase alphabet.
 */
final class FlagKeys {
    static final String PATTERN = "^[a-z0-9]+(?:[-_.][a-z0-9]+)*$";
    static final String MESSAGE =
            "must be lowercase letters and digits, optionally separated by single '-', '_' or '.'";

    private FlagKeys() {}
}
