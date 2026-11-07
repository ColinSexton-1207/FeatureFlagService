package com.example.flagship.flag.application;

import com.example.flagship.flag.domain.FlagType;

public record CreateFlagCommand(String key, String name, String description, FlagType flagType) {}
