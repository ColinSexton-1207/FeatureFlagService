package com.example.flagship.flag.application;

import com.example.flagship.flag.infrastructure.FeatureFlagRepository;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListFlagsService {
    private final DefaultProjectResolver projectResolver;
    private final FeatureFlagRepository flags;

    ListFlagsService(DefaultProjectResolver projectResolver, FeatureFlagRepository flags) {
        this.projectResolver = projectResolver;
        this.flags = flags;
    }

    /** Always sorted by key, so paging is stable between calls. */
    @Transactional(readOnly = true)
    public Page<FlagDetails> list(ListFlagsQuery query) {
        PageRequest pageable = PageRequest.of(query.page(), query.size(), Sort.by("key"));

        return flags.search(projectResolver.defaultProjectId(),
                        query.status().includesActive(), query.status().includesArchived(),
                        containsPattern(query.text()), pageable)
                .map(FlagDetails::from);
    }

    /** LIKE pattern that treats %, _ and the escape character in user input literally. */
    static String containsPattern(String text) {
        if (text == null) { return "%"; }

        String escaped = text.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");

        return "%" + escaped + "%";
    }
}
