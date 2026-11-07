package com.example.flagship.flag.api;

import com.example.flagship.flag.application.UpdateConfigurationCommand;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Full replacement body for PUT. Every client-editable field is required. Server-managed fields
 * (version, updatedAt, updatedBy) are deliberately absent, so sending them is rejected as an
 * unknown property. Range, user-ID, duplicate and overlap rules live in the domain.
 */
public record UpdateConfigurationRequest(
        @NotNull Boolean enabled,
        @NotNull Integer rolloutPercentage,
        @NotNull List<String> allowUsers,
        @NotNull List<String> denyUsers) {

    UpdateConfigurationCommand toCommand() {
        return new UpdateConfigurationCommand(enabled, rolloutPercentage, allowUsers, denyUsers);
    }
}
