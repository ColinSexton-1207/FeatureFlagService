package com.example.flagship.flag.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FlagConfigurationRulesTest {

    @Test
    void acceptsAValidConfigurationAndKeepsListOrder() {
        FlagConfigurationRules rules = new FlagConfigurationRules(
                true, 20, List.of("user-200", "user-100"), List.of("user-900"));

        assertThat(rules.enabled()).isTrue();
        assertThat(rules.rolloutPercentage()).isEqualTo(20);
        assertThat(rules.allowUsers()).containsExactly("user-200", "user-100");
        assertThat(rules.denyUsers()).containsExactly("user-900");
    }

    @Test
    void zeroAndOneHundredPercentAreValidBoundaries() {
        new FlagConfigurationRules(false, 0, List.of(), List.of());
        new FlagConfigurationRules(true, 100, List.of(), List.of());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void rejectsRolloutOutsideZeroToOneHundred(int rollout) {
        assertViolation(() -> new FlagConfigurationRules(true, rollout, List.of(), List.of()),
                "rolloutPercentage", "must be between 0 and 100");
    }

    @Test
    void userIdsAreCaseSensitiveSoDifferentCaseIsNotADuplicate() {
        FlagConfigurationRules rules = new FlagConfigurationRules(
                true, 0, List.of("User-1", "user-1"), List.of());
        assertThat(rules.allowUsers()).containsExactly("User-1", "user-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "has space", "tab\tid", "naïve", "line\nbreak"})
    void rejectsUserIdsThatAreBlankContainWhitespaceOrAreNotPrintableAscii(String badId) {
        assertViolation(() -> new FlagConfigurationRules(true, 0, List.of("ok", badId), List.of()),
                "allowUsers[1]", FlagConfigurationRules.USER_ID_MESSAGE);
    }

    @Test
    void rejectsUserIdsLongerThan200Characters() {
        String tooLong = "u".repeat(201);
        new FlagConfigurationRules(true, 0, List.of(), List.of("u".repeat(200)));
        assertViolation(() -> new FlagConfigurationRules(true, 0, List.of(), List.of(tooLong)),
                "denyUsers[0]", FlagConfigurationRules.USER_ID_MESSAGE);
    }

    @Test
    void rejectsNullUserIds() {
        assertViolation(() -> new FlagConfigurationRules(true, 0, Arrays.asList("a", null), List.of()),
                "allowUsers[1]", "must not be null");
    }

    @Test
    void rejectsListsLongerThanTheLimit() {
        List<String> tooMany = IntStream.rangeClosed(0, FlagConfigurationRules.MAX_USERS_PER_LIST)
                .mapToObj(i -> "user-" + i)
                .toList();
        assertViolation(() -> new FlagConfigurationRules(true, 0, tooMany, List.of()),
                "allowUsers", "must not contain more than 1000 user IDs");
    }

    @Test
    void rejectsDuplicatesWithinOneList() {
        assertViolation(() -> new FlagConfigurationRules(true, 0, List.of(), List.of("u1", "u2", "u1")),
                "denyUsers", "contains duplicate user ID 'u1'");
    }

    @Test
    void rejectsAUserThatIsBothAllowedAndDenied() {
        assertViolation(() -> new FlagConfigurationRules(true, 0, List.of("u1", "u2"), List.of("u2")),
                "denyUsers", "user ID 'u2' also appears in allowUsers");
    }

    @Test
    void reportsEveryViolationAtOnce() {
        assertThatThrownBy(() -> new FlagConfigurationRules(true, 150, List.of("a", "a"), List.of("a")))
                .isInstanceOfSatisfying(InvalidConfigurationException.class, e ->
                        assertThat(e.getViolations()).extracting(Violation::field)
                                .containsExactlyInAnyOrder("rolloutPercentage", "allowUsers", "denyUsers"));
    }

    @Test
    void storedListsCannotBeModifiedThroughTheCallersReference() {
        List<String> allow = new ArrayList<>(List.of("u1"));
        FlagConfigurationRules rules = new FlagConfigurationRules(true, 0, allow, Collections.emptyList());
        allow.add("u2");

        assertThat(rules.allowUsers()).containsExactly("u1");
    }

    @Test
    void equalityComparesEveryFieldIncludingListOrder() {
        FlagConfigurationRules a = new FlagConfigurationRules(true, 10, List.of("u1", "u2"), List.of());
        assertThat(a).isEqualTo(new FlagConfigurationRules(true, 10, List.of("u1", "u2"), List.of()));
        assertThat(a).isNotEqualTo(new FlagConfigurationRules(true, 10, List.of("u2", "u1"), List.of()));
        assertThat(a).isNotEqualTo(new FlagConfigurationRules(false, 10, List.of("u1", "u2"), List.of()));
        assertThat(a).isNotEqualTo(new FlagConfigurationRules(true, 11, List.of("u1", "u2"), List.of()));
    }

    private static void assertViolation(Runnable action, String field, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(InvalidConfigurationException.class, e ->
                        assertThat(e.getViolations()).contains(new Violation(field, message)));
    }
}
