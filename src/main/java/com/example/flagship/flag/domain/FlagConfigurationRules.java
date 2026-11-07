package com.example.flagship.flag.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The client-editable part of one environment's configuration, valid by construction. Construction
 * reports every broken rule at once rather than stopping at the first.
 *
 * <p>User IDs are opaque and case-sensitive. Lists keep the order the client sent; equality is
 * order-sensitive, so an order-only change counts as a change.
 */
public record FlagConfigurationRules(boolean enabled, int rolloutPercentage,
                                     List<String> allowUsers, List<String> denyUsers) {

    public static final int MAX_USERS_PER_LIST = 1000;
    public static final String USER_ID_MESSAGE =
            "must be 1-200 printable ASCII characters with no whitespace";

    private static final Pattern USER_ID = Pattern.compile("^[\\x21-\\x7E]{1,200}$");

    public FlagConfigurationRules {
        List<Violation> violations = new ArrayList<>();

        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            violations.add(new Violation("rolloutPercentage", "must be between 0 and 100"));
        }
        checkList("allowUsers", allowUsers, violations);
        checkList("denyUsers", denyUsers, violations);

        if (allowUsers != null && denyUsers != null) {
            Set<String> allowed = new HashSet<>(allowUsers);
            denyUsers.stream()
                    .filter(allowed::contains)
                    .distinct()
                    .forEach(id -> violations.add(
                            new Violation("denyUsers", "user ID '" + id + "' also appears in allowUsers")));
        }

        if (!violations.isEmpty()) {
            throw new InvalidConfigurationException(violations);
        }

        // List.copyOf would reject nulls with an NPE; nulls are already reported above.
        allowUsers = Collections.unmodifiableList(new ArrayList<>(allowUsers));
        denyUsers = Collections.unmodifiableList(new ArrayList<>(denyUsers));
    }

    private static void checkList(String field, List<String> ids, List<Violation> violations) {
        if (ids == null) {
            violations.add(new Violation(field, "must not be null"));
            return;
        }

        if (ids.size() > MAX_USERS_PER_LIST) {
            violations.add(new Violation(field, "must not contain more than " + MAX_USERS_PER_LIST + " user IDs"));
            return;
        }

        Set<String> seen = new HashSet<>();
        Set<String> reported = new HashSet<>();

        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (id == null) {
                violations.add(new Violation(field + "[" + i + "]", "must not be null"));
            } 
            else if (!USER_ID.matcher(id).matches()) {
                violations.add(new Violation(field + "[" + i + "]", USER_ID_MESSAGE));
            } 
            else if (!seen.add(id) && reported.add(id)) {
                violations.add(new Violation(field, "contains duplicate user ID '" + id + "'"));
            }
        }
    }
}
