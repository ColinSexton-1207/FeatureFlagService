package com.example.flagship.flag.application;

/**
 * Another transaction committed a change to the same configuration between this request's read
 * and its write. Detects concurrent database writes only; a client-supplied expected version
 * (stale-client protection) arrives in a later slice.
 */
public class ConfigurationVersionConflictException extends RuntimeException {
    public ConfigurationVersionConflictException(String flagKey, String environmentKey, Throwable cause) {
        super("Configuration for flag '" + flagKey + "' in environment '" + environmentKey
                + "' was changed concurrently; re-read it and retry", cause);
    }
}
