package com.example.flagship.flag.api;

import com.example.flagship.flag.application.FlagConfigurationQueryService;
import com.example.flagship.flag.application.UpdateFlagConfigurationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flags/{key}/configurations")
class FlagConfigurationController {
    private final FlagConfigurationQueryService queries;
    private final UpdateFlagConfigurationService updates;

    FlagConfigurationController(FlagConfigurationQueryService queries, UpdateFlagConfigurationService updates) {
        this.queries = queries;
        this.updates = updates;
    }

    @GetMapping
    List<ConfigurationResponse> list(@PathVariable String key) {
        return queries.list(key).stream().map(ConfigurationResponse::from).toList();
    }

    @GetMapping("/{env}")
    ConfigurationResponse get(@PathVariable String key, @PathVariable String env) {
        return ConfigurationResponse.from(queries.get(key, env));
    }

    @PutMapping("/{env}")
    ConfigurationResponse replace(@PathVariable String key, @PathVariable String env,
                                  @Valid @RequestBody UpdateConfigurationRequest request) {
        return ConfigurationResponse.from(updates.update(key, env, request.toCommand()));
    }
}
