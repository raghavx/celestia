package com.celestia.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** SPEC-008 US6 / SC-006 — the schema is real, complete, and re-runnable. */
class FlywayMigrationIT extends PersistenceIT {

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void everyInScopeTableExistsWithUtf8mb4() {
        for (String table : new String[] {
                "app_user", "birth_data", "chart", "horary_query", "dasha_period",
                "prediction", "geocode_cache", "inbound_event", "outbox_message"
        }) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() AND table_name = ?",
                    Integer.class, table);
            assertThat(count).as(table).isEqualTo(1);

            String collation = jdbc.queryForObject(
                    "SELECT table_collation FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() AND table_name = ?",
                    String.class, table);
            assertThat(collation).as(table + " collation").startsWith("utf8mb4");
        }
    }

    @Test
    void theDocumentedUniqueKeysExist() {
        assertThat(uniqueColumns("app_user")).contains("phone_e164");
        assertThat(uniqueColumns("birth_data")).contains("user_id");
        assertThat(uniqueColumns("geocode_cache")).contains("query_norm");
        assertThat(uniqueColumns("inbound_event")).contains("wa_message_id");
    }

    @Test
    void migrationsAreReRunnableAndValidate() {
        Flyway flyway = Flyway.configure().dataSource(dataSource).load();
        assertThat(flyway.migrate().migrationsExecuted).isZero(); // already applied by Boot
        flyway.validate(); // throws on a checksum mismatch
    }

    private java.util.List<String> uniqueColumns(String table) {
        return jdbc.queryForList(
                "SELECT k.column_name FROM information_schema.table_constraints t "
                        + "JOIN information_schema.key_column_usage k USING (constraint_name, table_schema, table_name) "
                        + "WHERE t.table_schema = DATABASE() AND t.table_name = ? AND t.constraint_type = 'UNIQUE'",
                String.class, table);
    }
}
