package com.example.flagship.flag.api;

import com.example.flagship.flag.application.ArchiveFlagService;
import com.example.flagship.flag.application.CreateFlagService;
import com.example.flagship.flag.application.GetFlagService;
import com.example.flagship.flag.application.ListFlagsService;
import com.example.flagship.flag.application.UpdateFlagService;
import com.fasterxml.jackson.databind.JsonNode;
import com.example.flagship.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/flags")
class FlagController {
    private final CreateFlagService createFlag;
    private final GetFlagService getFlag;
    private final ListFlagsService listFlags;
    private final UpdateFlagService updateFlag;
    private final ArchiveFlagService archiveFlag;

    FlagController(CreateFlagService createFlag, GetFlagService getFlag, ListFlagsService listFlags,
                   UpdateFlagService updateFlag, ArchiveFlagService archiveFlag) {
        this.createFlag = createFlag;
        this.getFlag = getFlag;
        this.listFlags = listFlags;
        this.updateFlag = updateFlag;
        this.archiveFlag = archiveFlag;
    }

    @GetMapping
    PageResponse<FlagResponse> list(@RequestParam(required = false) String status,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) String page,
                                    @RequestParam(required = false) String size) {
        return PageResponse.from(listFlags.list(ListFlagsParams.parse(status, q, page, size)), FlagResponse::from);
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

    @PatchMapping("/{key}")
    FlagResponse update(@PathVariable String key, @RequestBody JsonNode body) {
        return FlagResponse.from(updateFlag.update(key, FlagPatchParser.parse(body)));
    }

    /** Archives; never hard-deletes. Idempotent: archiving an archived flag is also 204. */
    @DeleteMapping("/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable String key) {
        archiveFlag.archive(key);
    }
}
