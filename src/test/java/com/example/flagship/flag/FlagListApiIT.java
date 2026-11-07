package com.example.flagship.flag;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.flagship.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Other test classes share this database, so every test scopes its assertions with a unique
 * key prefix passed as {@code q}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FlagListApiIT {

    @Autowired MockMvc mvc;

    @Test
    void listsFlagsSortedByKeyInAPageEnvelope() throws Exception {
        String p = prefix();
        create(p + "-charlie", "Charlie");
        create(p + "-alpha", "Alpha");
        create(p + "-bravo", "Bravo");

        mvc.perform(get("/api/v1/flags").param("q", p))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].key", contains(p + "-alpha", p + "-bravo", p + "-charlie")))
                .andExpect(jsonPath("$.content[0].name").value("Alpha"))
                .andExpect(jsonPath("$.content[0].id").isString())
                .andExpect(jsonPath("$.content[0].archivedAt").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void pagesThroughResults() throws Exception {
        String p = prefix();
        for (String s : new String[] {"a", "b", "c", "d", "e"}) {
            create(p + "-" + s, "Flag " + s);
        }

        mvc.perform(get("/api/v1/flags").param("q", p).param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].key", contains(p + "-c", p + "-d")))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));

        mvc.perform(get("/api/v1/flags").param("q", p).param("size", "2").param("page", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void statusFilterActsLikeTabsAndDefaultsToActive() throws Exception {
        String p = prefix();
        create(p + "-live", "Live");
        create(p + "-old", "Old");
        mvc.perform(delete("/api/v1/flags/{key}", p + "-old")).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/flags").param("q", p))
                .andExpect(jsonPath("$.content[*].key", contains(p + "-live")));
        mvc.perform(get("/api/v1/flags").param("q", p).param("status", "active"))
                .andExpect(jsonPath("$.content[*].key", contains(p + "-live")));
        mvc.perform(get("/api/v1/flags").param("q", p).param("status", "archived"))
                .andExpect(jsonPath("$.content[*].key", contains(p + "-old")))
                .andExpect(jsonPath("$.content[0].archivedAt").isString());
        mvc.perform(get("/api/v1/flags").param("q", p).param("status", "ALL"))
                .andExpect(jsonPath("$.content[*].key", contains(p + "-live", p + "-old")))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void queryMatchesKeyOrNameCaseInsensitively() throws Exception {
        String p = prefix();
        create(p + "-checkout", "Unrelated name");
        create(p + "-other", "The New CHECKOUT page");
        create(p + "-nope", "Nothing here");

        mvc.perform(get("/api/v1/flags").param("q", p))
                .andExpect(jsonPath("$.totalElements").value(3));
        // The name match for "-other" proves name search; the key match proves key search.
        mvc.perform(get("/api/v1/flags").param("q", "checkout").param("size", "100"))
                .andExpect(jsonPath("$.content[*].key", hasItem(p + "-checkout")))
                .andExpect(jsonPath("$.content[*].key", hasItem(p + "-other")));
        mvc.perform(get("/api/v1/flags").param("q", p.toUpperCase()))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void likeWildcardsInQueryAreMatchedLiterally() throws Exception {
        String p = prefix();
        create(p + "-plain", "Plain");
        create(p + "-pct", "Has 100% in it");

        mvc.perform(get("/api/v1/flags").param("q", "100%").param("size", "100"))
                .andExpect(jsonPath("$.content[*].key", hasItem(p + "-pct")));
        // Keys are "<p>-plain" and "<p>-pct". An unescaped '_' matches any single character, so
        // "<p>_" would match both via the '-'; matched literally it matches neither.
        mvc.perform(get("/api/v1/flags").param("q", p + "_"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/flags").param("q", p + "-"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @ParameterizedTest
    @CsvSource({
            "page, -1",
            "page, abc",
            "size, 0",
            "size, 101",
            "size, x",
            "status, deleted",
    })
    void invalidParametersAre400ValidationErrors(String param, String value) throws Exception {
        mvc.perform(get("/api/v1/flags").param(param, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem(param)));
    }

    @Test
    void queryLongerThan100CharactersIsRejected() throws Exception {
        mvc.perform(get("/api/v1/flags").param("q", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("q")));
    }

    @Test
    void sizeOf100IsAllowed() throws Exception {
        mvc.perform(get("/api/v1/flags").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    private void create(String key, String name) throws Exception {
        mvc.perform(post("/api/v1/flags").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"%s\",\"name\":\"%s\"}".formatted(key, name)))
                .andExpect(status().isCreated());
    }

    /** Lowercase hex plus "lst", valid as a flag-key segment and unique per test. */
    private static String prefix() {
        return "lst" + UUID.randomUUID().toString().substring(0, 8);
    }
}
