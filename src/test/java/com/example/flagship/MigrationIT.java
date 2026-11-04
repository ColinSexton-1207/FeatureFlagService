package com.example.flagship;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MigrationIT {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void allMigrationsApplySuccessfullyToAnEmptyDatabase() {
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied())
                .isNotEmpty()
                .allSatisfy(m -> assertThat(m.getState().isFailed()).isFalse());
    }

    @Test
    void seedsOneDefaultProjectWithTheFourEnvironmentsInOrder() {
        assertThat(jdbc.queryForList("SELECT key FROM projects", String.class)).containsExactly("default");

        List<String> envKeys = jdbc.queryForList("""
                SELECT e.key FROM environments e
                JOIN projects p ON p.id = e.project_id
                WHERE p.key = 'default'
                ORDER BY e.sort_order
                """, String.class);
        assertThat(envKeys).containsExactly("dev", "qa", "uat", "prod");
    }

    @Test
    void reRunningMigrateIsANoOpAndDoesNotDuplicateSeedData() {
        flyway.migrate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM environments", Integer.class)).isEqualTo(4);
    }
}
