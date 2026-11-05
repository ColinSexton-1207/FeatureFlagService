package com.example.flagship.flag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.flagship.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Exercises the flag API over HTTP against a real PostgreSQL. Each test uses its own flag key,
 * so tests stay independent without truncating tables between them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FlagApiIT {

    private static final String ISO_INSTANT = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createReturns201WithGeneratedIdTimestampsAndLocation() throws Exception {
        String key = uniqueKey("new-checkout");

        createFlag("""
                {"key":"%s","name":"New Checkout Experience",
                 "description":"Gradual rollout of the redesigned checkout","flagType":"BOOLEAN"}
                """.formatted(key))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/flags/" + key)))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.key").value(key))
                .andExpect(jsonPath("$.name").value("New Checkout Experience"))
                .andExpect(jsonPath("$.description").value("Gradual rollout of the redesigned checkout"))
                .andExpect(jsonPath("$.flagType").value("BOOLEAN"))
                .andExpect(jsonPath("$.createdBy").value("system"))
                .andExpect(jsonPath("$.createdAt", matchesPattern(ISO_INSTANT)))
                .andExpect(jsonPath("$.updatedAt", matchesPattern(ISO_INSTANT)))
                .andExpect(jsonPath("$.archivedAt").isEmpty());
    }

    @Test
    void createAlsoCreatesOneDisabledZeroPercentConfigurationPerEnvironment() throws Exception {
        String key = uniqueKey("env-configs");
        createFlag(minimalBody(key)).andExpect(status().isCreated());

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT e.key AS env, c.enabled, c.rollout_percentage, c.version,
                       c.allow_users::text AS allow_users, c.deny_users::text AS deny_users
                FROM flag_configurations c
                JOIN feature_flags f ON f.id = c.flag_id
                JOIN environments e ON e.id = c.environment_id
                WHERE f.key = ?
                ORDER BY e.sort_order
                """, key);

        assertThat(rows).extracting(r -> r.get("env")).containsExactly("dev", "qa", "uat", "prod");
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.get("enabled")).isEqualTo(false);
            assertThat(((Number) r.get("rollout_percentage")).intValue()).isZero();
            assertThat(((Number) r.get("version")).longValue()).isZero();
            assertThat(r.get("allow_users")).isEqualTo("[]");
            assertThat(r.get("deny_users")).isEqualTo("[]");
        });
    }

    @Test
    void flagTypeDefaultsToBooleanWhenOmitted() throws Exception {
        String key = uniqueKey("default-type");
        createFlag(minimalBody(key))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flagType").value("BOOLEAN"));
    }

    @Test
    void getReturnsWhatWasCreated() throws Exception {
        String key = uniqueKey("roundtrip");
        ResultActions created = createFlag(minimalBody(key)).andExpect(status().isCreated());
        String id = jsonPathString(created, "$.id");
        String createdAt = jsonPathString(created, "$.createdAt");
        String updatedAt = jsonPathString(created, "$.updatedAt");

        mvc.perform(get("/api/v1/flags/{key}", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.key").value(key))
                .andExpect(jsonPath("$.name").value("Test flag"))
                .andExpect(jsonPath("$.flagType").value("BOOLEAN"))
                // PostgreSQL stores microseconds; the create response must not report more precision
                // than a later read can return.
                .andExpect(jsonPath("$.createdAt").value(createdAt))
                .andExpect(jsonPath("$.updatedAt").value(updatedAt));
    }

    @Test
    void getUnknownFlagReturns404Problem() throws Exception {
        mvc.perform(get("/api/v1/flags/{key}", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("FLAG_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/api/v1/flags/does-not-exist"))
                .andExpect(jsonPath("$.detail").value("Flag 'does-not-exist' was not found"));
    }

    @Test
    void duplicateKeyReturns409AndDoesNotCreateExtraRows() throws Exception {
        String key = uniqueKey("dupe");
        createFlag(minimalBody(key)).andExpect(status().isCreated());

        createFlag(minimalBody(key))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FLAG_KEY_CONFLICT"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM feature_flags WHERE key = ?", Integer.class, key))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM flag_configurations c JOIN feature_flags f ON f.id = c.flag_id
                WHERE f.key = ?""", Integer.class, key))
                .isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Upper-Case", "has space", "-leading-dash", "trailing-", "double--dash", "colon:key", ""})
    void invalidKeyReturns400WithFieldError(String badKey) throws Exception {
        createFlag("""
                {"key":"%s","name":"Bad key"}
                """.formatted(badKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("key"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM feature_flags WHERE key = ?", Integer.class, badKey))
                .isZero();
    }

    @Test
    void missingNameReturns400() throws Exception {
        createFlag("""
                {"key":"%s"}
                """.formatted(uniqueKey("no-name")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void malformedJsonReturns400WithoutLeakingParserDetails() throws Exception {
        createFlag("{\"key\": \"oops\", ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Request body is missing or is not valid JSON for this endpoint"));
    }

    @Test
    void unknownFlagTypeIsRejectedAsMalformed() throws Exception {
        createFlag("""
                {"key":"%s","name":"x","flagType":"MULTIVARIATE"}
                """.formatted(uniqueKey("bad-type")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    private ResultActions createFlag(String json) throws Exception {
        return mvc.perform(post("/api/v1/flags").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String minimalBody(String key) {
        return """
                {"key":"%s","name":"Test flag"}
                """.formatted(key);
    }

    private static String uniqueKey(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String jsonPathString(ResultActions result, String path) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, path);
    }
}
