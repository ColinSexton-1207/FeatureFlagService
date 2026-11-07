package com.example.flagship.flag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.flagship.TestcontainersConfiguration;
import com.example.flagship.flag.application.ConfigurationVersionConflictException;
import com.example.flagship.flag.application.UpdateConfigurationCommand;
import com.example.flagship.flag.application.UpdateFlagConfigurationService;
import com.example.flagship.flag.infrastructure.FlagConfigurationRepository;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FlagConfigurationApiIT {

    private static final String PROD_ENV_ID = "00000000-0000-0000-0000-000000000104";
    private static final String VALID_PUT = """
            {"enabled":true,"rolloutPercentage":20,"allowUsers":["user-100","user-200"],"denyUsers":["user-900"]}
            """;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UpdateFlagConfigurationService updateService;
    @Autowired FlagConfigurationRepository configurationRepository;
    @Autowired PlatformTransactionManager transactionManager;

    // ---- GET ----

    @Test
    void listReturnsAllFourEnvironmentsInDefinedOrderWithDefaults() throws Exception {
        String key = createFlag("list-configs");

        mvc.perform(get("/api/v1/flags/{key}/configurations", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].environment", contains("dev", "qa", "uat", "prod")))
                .andExpect(jsonPath("$[*].flagKey", contains(key, key, key, key)))
                .andExpect(jsonPath("$[*].enabled", contains(false, false, false, false)))
                .andExpect(jsonPath("$[*].rolloutPercentage", contains(0, 0, 0, 0)))
                .andExpect(jsonPath("$[*].version", contains(0, 0, 0, 0)))
                .andExpect(jsonPath("$[0].allowUsers").isEmpty())
                .andExpect(jsonPath("$[0].denyUsers").isEmpty())
                .andExpect(jsonPath("$[0].updatedBy").value("system"))
                .andExpect(jsonPath("$[0].updatedAt").isString());
    }

    @Test
    void listOrderFollowsSortOrderNotInsertionOrder() throws Exception {
        String key = createFlag("order");
        // Updating dev's row writes a new tuple at the end of the heap (PostgreSQL MVCC), so an
        // unordered scan would now return dev last. Only an explicit ORDER BY keeps dev first.
        jdbc.update("""
                UPDATE flag_configurations SET rollout_percentage = 7
                WHERE flag_id = (SELECT id FROM feature_flags WHERE key = ?) AND environment_id =
                      '00000000-0000-0000-0000-000000000101'""", key);

        mvc.perform(get("/api/v1/flags/{key}/configurations", key))
                .andExpect(jsonPath("$[*].environment", contains("dev", "qa", "uat", "prod")));
    }

    @Test
    void getOneReturnsThatEnvironment() throws Exception {
        String key = createFlag("get-one");

        mvc.perform(get("/api/v1/flags/{key}/configurations/{env}", key, "qa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagKey").value(key))
                .andExpect(jsonPath("$.environment").value("qa"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void unknownFlagIs404ForListGetAndPut() throws Exception {
        mvc.perform(get("/api/v1/flags/{key}/configurations", "no-such-flag"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FLAG_NOT_FOUND"));
        mvc.perform(get("/api/v1/flags/{key}/configurations/{env}", "no-such-flag", "prod"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FLAG_NOT_FOUND"));
        putConfig("no-such-flag", "prod", VALID_PUT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FLAG_NOT_FOUND"));
    }

    @Test
    void unknownEnvironmentIs404ForGetAndPut() throws Exception {
        String key = createFlag("bad-env");

        mvc.perform(get("/api/v1/flags/{key}/configurations/{env}", key, "staging"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENVIRONMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Environment 'staging' was not found"));
        putConfig(key, "staging", VALID_PUT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENVIRONMENT_NOT_FOUND"));
    }

    // ---- PUT happy paths ----

    @Test
    void putReplacesTheConfigurationAndIncrementsVersion() throws Exception {
        String key = createFlag("put-ok");

        putConfig(key, "prod", VALID_PUT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagKey").value(key))
                .andExpect(jsonPath("$.environment").value("prod"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.rolloutPercentage").value(20))
                .andExpect(jsonPath("$.allowUsers", contains("user-100", "user-200")))
                .andExpect(jsonPath("$.denyUsers", contains("user-900")))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.updatedBy").value("system"));

        mvc.perform(get("/api/v1/flags/{key}/configurations/{env}", key, "prod"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.rolloutPercentage").value(20))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void putChangesOnlyTheTargetedEnvironment() throws Exception {
        String key = createFlag("isolated");
        putConfig(key, "qa", VALID_PUT).andExpect(status().isOk());

        mvc.perform(get("/api/v1/flags/{key}/configurations", key))
                .andExpect(jsonPath("$[*].enabled", contains(false, true, false, false)))
                .andExpect(jsonPath("$[*].version", contains(0, 1, 0, 0)));
    }

    @Test
    void identicalPutDoesNotChangeVersionOrUpdatedAt() throws Exception {
        String key = createFlag("idempotent");
        String first = body(putConfig(key, "prod", VALID_PUT).andExpect(status().isOk()));

        String second = body(putConfig(key, "prod", VALID_PUT).andExpect(status().isOk()));

        assertThat(JsonPath.<Integer>read(second, "$.version")).isEqualTo(1);
        assertThat(JsonPath.<String>read(second, "$.updatedAt"))
                .isEqualTo(JsonPath.<String>read(first, "$.updatedAt"));
        assertThat(dbVersion(key)).isEqualTo(1);
    }

    @Test
    void identicalPutToTheUntouchedDefaultsIsAlsoANoOp() throws Exception {
        String key = createFlag("noop-default");
        putConfig(key, "prod", """
                {"enabled":false,"rolloutPercentage":0,"allowUsers":[],"denyUsers":[]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void eachRealChangeIncrementsVersionAgain() throws Exception {
        String key = createFlag("twice");
        putConfig(key, "prod", VALID_PUT).andExpect(jsonPath("$.version").value(1));
        putConfig(key, "prod", """
                {"enabled":true,"rolloutPercentage":25,"allowUsers":["user-100","user-200"],"denyUsers":["user-900"]}
                """).andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void reorderingAListCountsAsAChange() throws Exception {
        String key = createFlag("reorder");
        putConfig(key, "prod", VALID_PUT).andExpect(jsonPath("$.version").value(1));
        putConfig(key, "prod", """
                {"enabled":true,"rolloutPercentage":20,"allowUsers":["user-200","user-100"],"denyUsers":["user-900"]}
                """)
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.allowUsers", contains("user-200", "user-100")));
    }

    @Test
    void userIdsAreCaseSensitive() throws Exception {
        String key = createFlag("case");
        putConfig(key, "prod", """
                {"enabled":true,"rolloutPercentage":0,"allowUsers":["User-1"],"denyUsers":["user-1"]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowUsers", contains("User-1")))
                .andExpect(jsonPath("$.denyUsers", contains("user-1")));
    }

    // ---- PUT rejections: each leaves the row untouched ----

    @ParameterizedTest
    @ValueSource(strings = {"enabled", "rolloutPercentage", "allowUsers", "denyUsers"})
    void missingFieldIsRejected(String field) throws Exception {
        String key = createFlag("missing");
        String json = switch (field) {
            case "enabled" -> """
                    {"rolloutPercentage":20,"allowUsers":[],"denyUsers":[]}""";
            case "rolloutPercentage" -> """
                    {"enabled":true,"allowUsers":[],"denyUsers":[]}""";
            case "allowUsers" -> """
                    {"enabled":true,"rolloutPercentage":20,"denyUsers":[]}""";
            default -> """
                    {"enabled":true,"rolloutPercentage":20,"allowUsers":[]}""";
        };

        assertRejected(key, json, "VALIDATION_FAILED", field);
    }

    @Test
    void explicitNullFieldIsRejected() throws Exception {
        String key = createFlag("null-field");
        assertRejected(key, """
                {"enabled":null,"rolloutPercentage":20,"allowUsers":[],"denyUsers":[]}
                """, "VALIDATION_FAILED", "enabled");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101})
    void rolloutOutOfRangeIsRejected(int rollout) throws Exception {
        String key = createFlag("range");
        assertRejected(key, """
                {"enabled":true,"rolloutPercentage":%d,"allowUsers":[],"denyUsers":[]}
                """.formatted(rollout), "VALIDATION_FAILED", "rolloutPercentage");
    }

    @ParameterizedTest
    @ValueSource(strings = {"25.5", "\"25\"", "20.0"})
    void nonIntegerRolloutIsRejectedNotCoerced(String rollout) throws Exception {
        String key = createFlag("coerce");
        assertRejected(key, """
                {"enabled":true,"rolloutPercentage":%s,"allowUsers":[],"denyUsers":[]}
                """.formatted(rollout), "MALFORMED_REQUEST", null);
    }

    @Test
    void stringBooleanIsRejectedNotCoerced() throws Exception {
        String key = createFlag("coerce-bool");
        assertRejected(key, """
                {"enabled":"true","rolloutPercentage":20,"allowUsers":[],"denyUsers":[]}
                """, "MALFORMED_REQUEST", null);
    }

    @Test
    void duplicateUserInOneListIsRejected() throws Exception {
        String key = createFlag("dupe-user");
        assertRejected(key, """
                {"enabled":true,"rolloutPercentage":20,"allowUsers":["u1","u1"],"denyUsers":[]}
                """, "VALIDATION_FAILED", "allowUsers");
    }

    @Test
    void userInBothListsIsRejected() throws Exception {
        String key = createFlag("overlap");
        assertRejected(key, """
                {"enabled":true,"rolloutPercentage":20,"allowUsers":["u1"],"denyUsers":["u1"]}
                """, "VALIDATION_FAILED", "denyUsers");
    }

    @Test
    void invalidUserIdIsRejectedWithItsIndex() throws Exception {
        String key = createFlag("bad-user");
        assertRejected(key, """
                {"enabled":true,"rolloutPercentage":20,"allowUsers":["ok","has space"],"denyUsers":[]}
                """, "VALIDATION_FAILED", "allowUsers[1]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "updatedAt", "updatedBy", "somethingElse"})
    void serverManagedOrUnknownFieldIsRejected(String field) throws Exception {
        String key = createFlag("readonly");
        assertRejected(key, """
                {"enabled":true,"rolloutPercentage":20,"allowUsers":[],"denyUsers":[],"%s":99}
                """.formatted(field), "VALIDATION_FAILED", field);
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        String key = createFlag("malformed");
        assertRejected(key, "{\"enabled\": true,", "MALFORMED_REQUEST", null);
    }

    @Test
    void rejectedPutAfterARealChangeKeepsTheSavedState() throws Exception {
        String key = createFlag("keep");
        putConfig(key, "prod", VALID_PUT).andExpect(status().isOk());

        putConfig(key, "prod", """
                {"enabled":false,"rolloutPercentage":101,"allowUsers":[],"denyUsers":[]}
                """).andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/flags/{key}/configurations/{env}", key, "prod"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.rolloutPercentage").value(20))
                .andExpect(jsonPath("$.version").value(1));
    }

    // ---- Archived flags ----

    @Test
    void putOnArchivedFlagIs409AndChangesNothing() throws Exception {
        String key = createFlag("archived-put");
        archive(key);

        putConfig(key, "prod", VALID_PUT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FLAG_ARCHIVED"));

        assertThat(dbVersion(key)).isZero();
    }

    @Test
    void archivedFlagConfigurationsRemainReadable() throws Exception {
        String key = createFlag("archived-read");
        archive(key);

        mvc.perform(get("/api/v1/flags/{key}/configurations", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].environment", contains("dev", "qa", "uat", "prod")));
    }

    // ---- Concurrency ----

    /**
     * Interleaves two writers deterministically: this transaction reads version 0, another
     * transaction commits version 1, then this one tries to write based on its stale read.
     */
    @Test
    void writerThatLosesARaceGetsAVersionConflictAndDoesNotOverwrite() throws Exception {
        String key = createFlag("race");
        UUID flagId = jdbc.queryForObject("SELECT id FROM feature_flags WHERE key = ?", UUID.class, key);

        TransactionTemplate slowWriter = new TransactionTemplate(transactionManager);
        TransactionTemplate fastWriter = new TransactionTemplate(transactionManager);
        fastWriter.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> slowWriter.executeWithoutResult(tx -> {
            // Load version 0 into the slow writer's persistence context.
            configurationRepository.findByFlagIdAndEnvironmentId(flagId, UUID.fromString(PROD_ENV_ID)).orElseThrow();

            fastWriter.executeWithoutResult(inner -> jdbc.update("""
                    UPDATE flag_configurations SET rollout_percentage = 50, version = version + 1
                    WHERE flag_id = ? AND environment_id = ?::uuid""", flagId, PROD_ENV_ID));

            updateService.update(key, "prod",
                    new UpdateConfigurationCommand(true, 10, List.of(), List.of()));
        })).isInstanceOf(ConfigurationVersionConflictException.class);

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT rollout_percentage, enabled, version FROM flag_configurations
                WHERE flag_id = ? AND environment_id = ?::uuid""", flagId, PROD_ENV_ID);
        assertThat(((Number) row.get("rollout_percentage")).intValue()).isEqualTo(50);
        assertThat(row.get("enabled")).isEqualTo(false);
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(1);
    }

    // ---- helpers ----

    private void assertRejected(String key, String json, String code, String field) throws Exception {
        ResultActions result = putConfig(key, "prod", json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
        if (field != null) {
            result.andExpect(jsonPath("$.errors[*].field", hasItem(field)));
        }
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT c.enabled, c.rollout_percentage, c.version, c.allow_users::text AS allow_users
                FROM flag_configurations c JOIN feature_flags f ON f.id = c.flag_id
                WHERE f.key = ? AND c.environment_id = ?::uuid""", key, PROD_ENV_ID);
        assertThat(row.get("enabled")).isEqualTo(false);
        assertThat(((Number) row.get("rollout_percentage")).intValue()).isZero();
        assertThat(((Number) row.get("version")).longValue()).isZero();
        assertThat(row.get("allow_users")).isEqualTo("[]");
    }

    private long dbVersion(String key) {
        return jdbc.queryForObject("""
                SELECT c.version FROM flag_configurations c JOIN feature_flags f ON f.id = c.flag_id
                WHERE f.key = ? AND c.environment_id = ?::uuid""", Long.class, key, PROD_ENV_ID);
    }

    private ResultActions putConfig(String key, String env, String json) throws Exception {
        return mvc.perform(put("/api/v1/flags/{key}/configurations/{env}", key, env)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String createFlag(String prefix) throws Exception {
        String key = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/v1/flags").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"%s\",\"name\":\"Test flag\"}".formatted(key)))
                .andExpect(status().isCreated());
        return key;
    }

    private void archive(String key) throws Exception {
        mvc.perform(delete("/api/v1/flags/{key}", key)).andExpect(status().isNoContent());
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
