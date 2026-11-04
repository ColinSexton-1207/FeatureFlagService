package com.example.flagship.flag.api;

import com.example.flagship.flag.application.CreateFlagService;
import com.example.flagship.flag.application.GetFlagService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/flags")
class FlagController {

    private final CreateFlagService createFlag;
    private final GetFlagService getFlag;

    FlagController(CreateFlagService createFlag, GetFlagService getFlag) {
        this.createFlag = createFlag;
        this.getFlag = getFlag;
    }

    @PostMapping
    ResponseEntity<FlagResponse> create(@Valid @RequestBody CreateFlagRequest request) {
        FlagResponse body = FlagResponse.from(createFlag.create(request.toCommand()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{key}")
                .buildAndExpand(body.key())
                .toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping("/{key}")
    FlagResponse get(@PathVariable String key) {
        return FlagResponse.from(getFlag.get(key));
    }
}
