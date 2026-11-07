package com.example.flagship.flag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.flagship.TestcontainersConfiguration;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FlagLifecycleApiIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    // ---- PATCH ----

    @Test
    void patchUpdatesNameAndDescriptionAndBumpsUpdatedAt() throws Exception {
        String key = createFlag("patch-both");
        String before = body(getFlag(key));

        patchFlag(key, """
                {"name":"  Renamed  ","description":"New description"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value(key))
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.description").value("New description"))
                .andExpect(jsonPath("$.createdAt").value(JsonPath.<String>read(before, "$.createdAt")));

        String after = body(getFlag(key).andExpect(jsonPath("$.name").value("Renamed")));
        assertThat(JsonPath.<String>read(after, "$.updatedAt"))
                .isGreaterThan(JsonPath.<String>read(before, "$.updatedAt"));
    }

    @Test
    void omittedFieldIsLeftUnchanged() throws Exception {
        String key = createFlag("patch-name-only", "Original", "Keep me");

        patchFlag(key, """
                {"name":"Only name changes"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Only name changes"))
                .andExpect(jsonPath("$.description").value("Keep me"));
    }

    @Test
    void explicitNullDescriptionClearsIt() throws Exception {
        String key = createFlag("patch-clear", "Name", "Remove me");

        patchFlag(key, """
                {"description":null}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Name"))
                .andExpect(jsonPath("$.description").isEmpty());
        assertThat(jdbc.queryForObject("SELECT description FROM feature_flags WHERE key = ?", String.class, key))
                .isNull();
    }

    @Test
    void patchThatChangesNothingLeavesUpdatedAtAlone() throws Exception {
        String key = createFlag("patch-noop", "Same", "Same desc");
        String before = body(getFlag(key));

        patchFlag(key, """
                {"name":"Same","description":"Same desc"}
                """).andExpect(status().isOk());
        patchFlag(key, "{}").andExpect(status().isOk());

        assertThat(JsonPath.<String>read(body(getFlag(key)), "$.updatedAt"))
                .isEqualTo(JsonPath.<String>read(before, "$.updatedAt"));
    }

    @Test
    void patchDoesNotTouchConfigurations() throws Exception {
        String key = createFlag("patch-configs");
        patchFlag(key, "{\"name\":\"Changed\"}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM flag_configurations c JOIN feature_flags f ON f.id = c.flag_id
                WHERE f.key = ? AND c.version = 0""", Integer.class, key)).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":null}",
            "{\"name\":\"   \"}",
            "{\"name\":\"\"}",
    })
    void nameCannotBeNullOrBlank(String json) throws Exception {
        assertPatchRejected(json, "name");
    }

    @Test
    void lengthLimitsMatchCreate() throws Exception {
        assertPatchRejected("{\"name\":\"" + "n".repeat(201) + "\"}", "name");
        assertPatchRejected("{\"description\":\"" + "d".repeat(1001) + "\"}", "description");
    }

    @ParameterizedTest
    @ValueSource(strings = {"key", "flagType"})
    void immutableFieldsAreRejected(String field) throws Exception {
        assertPatchRejected("{\"%s\":\"BOOLEAN\"}".formatted(field), field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "createdAt", "archivedAt", "somethingElse"})
    void unknownOrServerManagedFieldsAreRejected(String field) throws Exception {
        assertPatchRejected("{\"name\":\"x\",\"%s\":\"v\"}".formatted(field), field);
    }

    @Test
    void wrongJsonTypeIsMalformed() throws Exception {
        String key = createFlag("patch-type", "Before", null);
        patchFlag(key, "{\"name\":5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        patchFlag(key, "[\"name\"]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        getFlag(key).andExpect(jsonPath("$.name").value("Before"));
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        String key = createFlag("patch-malformed");
        patchFlag(key, "{\"name\":")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void patchUnknownFlagIs404() throws Exception {
        patchFlag("no-such-flag", "{\"name\":\"x\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FLAG_NOT_FOUND"));
    }

    @Test
    void patchArchivedFlagIs409AndChangesNothing() throws Exception {
        String key = createFlag("patch-archived", "Before", null);
        archive(key).andExpect(status().isNoContent());

        patchFlag(key, "{\"name\":\"After\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FLAG_ARCHIVED"));
        getFlag(key).andExpect(jsonPath("$.name").value("Before"));
    }

    // ---- DELETE (archive) ----

    @Test
    void deleteArchivesTheFlagAndPreservesEveryConfiguration() throws Exception {
        String key = createFlag("archive-me");
        mvc.perform(put("/api/v1/flags/{key}/configurations/{env}", key, "prod")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"rolloutPercentage\":50,\"allowUsers\":[\"u1\"],\"denyUsers\":[]}"))
                .andExpect(status().isOk());
        List<Map<String, Object>> configsBefore = configRows(key);

        archive(key)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        getFlag(key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").isString());
        assertThat(configRows(key)).isEqualTo(configsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feature_flags WHERE key = ?", Integer.class, key))
                .isEqualTo(1);
    }

    @Test
    void archiveSetsUpdatedAtToTheArchiveTime() throws Exception {
        String key = createFlag("archive-time");
        archive(key);

        String flag = body(getFlag(key));
        assertThat(JsonPath.<String>read(flag, "$.updatedAt")).isEqualTo(JsonPath.<String>read(flag, "$.archivedAt"));
    }

    @Test
    void repeatDeleteIs204AndKeepsTheOriginalArchiveTime() throws Exception {
        String key = createFlag("archive-twice");
        archive(key).andExpect(status().isNoContent());
        String first = body(getFlag(key));

        archive(key).andExpect(status().isNoContent());

        String second = body(getFlag(key));
        assertThat(JsonPath.<String>read(second, "$.archivedAt")).isEqualTo(JsonPath.<String>read(first, "$.archivedAt"));
        assertThat(JsonPath.<String>read(second, "$.updatedAt")).isEqualTo(JsonPath.<String>read(first, "$.updatedAt"));
    }

    @Test
    void deleteUnknownFlagIs404() throws Exception {
        archive("no-such-flag")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FLAG_NOT_FOUND"));
    }

    // ---- helpers ----

    private void assertPatchRejected(String json, String field) throws Exception {
        String key = createFlag("patch-reject", "Before", "Desc before");
        patchFlag(key, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)));
        getFlag(key)
                .andExpect(jsonPath("$.name").value("Before"))
                .andExpect(jsonPath("$.description").value("Desc before"));
    }

    private List<Map<String, Object>> configRows(String key) {
        return jdbc.queryForList("""
                SELECT c.environment_id, c.enabled, c.rollout_percentage, c.allow_users::text AS allow_users,
                       c.deny_users::text AS deny_users, c.version, c.updated_at, c.updated_by
                FROM flag_configurations c JOIN feature_flags f ON f.id = c.flag_id
                WHERE f.key = ? ORDER BY c.environment_id""", key);
    }

    private ResultActions patchFlag(String key, String json) throws Exception {
        return mvc.perform(patch("/api/v1/flags/{key}", key).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions getFlag(String key) throws Exception {
        return mvc.perform(get("/api/v1/flags/{key}", key));
    }

    private ResultActions archive(String key) throws Exception {
        return mvc.perform(delete("/api/v1/flags/{key}", key));
    }

    private String createFlag(String prefix) throws Exception {
        return createFlag(prefix, "Test flag", null);
    }

    private String createFlag(String prefix, String name, String description) throws Exception {
        String key = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        String desc = description == null ? "" : ",\"description\":\"%s\"".formatted(description);
        mvc.perform(post("/api/v1/flags").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"%s\",\"name\":\"%s\"%s}".formatted(key, name, desc)))
                .andExpect(status().isCreated());
        return key;
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
