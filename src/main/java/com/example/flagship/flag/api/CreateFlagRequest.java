package com.example.flagship.flag.api;

import com.example.flagship.flag.application.CreateFlagCommand;
import com.example.flagship.flag.domain.FlagType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateFlagRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = FlagKeys.PATTERN, message = FlagKeys.MESSAGE)
        String key,

        @NotBlank
        @Size(max = 200)
        String name,

        @Size(max = 1000)
        String description,

        /* Optional; BOOLEAN is the only type in v1. */
        FlagType flagType) {

    CreateFlagCommand toCommand() {
        return new CreateFlagCommand(key, name.strip(), description, flagType);
    }
}
