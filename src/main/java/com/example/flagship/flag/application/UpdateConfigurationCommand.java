package com.example.flagship.flag.application;

import com.example.flagship.flag.domain.FlagConfigurationRules;
import java.util.List;

/** Full replacement of one environment's client-editable configuration. */
public record UpdateConfigurationCommand(boolean enabled, int rolloutPercentage,
                                         List<String> allowUsers, List<String> denyUsers) {

    FlagConfigurationRules toRules() {
        return new FlagConfigurationRules(enabled, rolloutPercentage, allowUsers, denyUsers);
    }
}
