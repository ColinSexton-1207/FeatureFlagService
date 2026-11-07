package com.example.flagship.flag.infrastructure;

/** A configuration row paired with the key of the environment it belongs to. */
public record EnvironmentConfiguration(FlagConfigurationEntity configuration, String environmentKey) {}
