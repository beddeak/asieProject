package com.asie.aegisvault.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AccountStatusSchemaTest {
    @Test
    void legacyStatusesArePreservedAndDeletedStatusIsAcceptedAfterUpgrade() throws Exception {
        var database = new DriverManagerDataSource("jdbc:h2:mem:schema-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = database.getConnection(); var statement = connection.createStatement()) {
            statement.execute("create table users(id bigint primary key, account_status enum('ACTIVE', 'BAN', 'LOCKED') not null)");
            statement.execute("insert into users values (1, 'ACTIVE'), (2, 'BAN'), (3, 'LOCKED')");
            var schema = new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"));
            schema.execute(database);
            schema.execute(database);
            statement.execute("insert into users values (4, 'DELETED')");
            try (var rows = statement.executeQuery("select account_status from users order by id")) {
                for (String expected : new String[]{"ACTIVE", "BAN", "LOCKED", "DELETED"}) {
                    rows.next();
                    assertEquals(expected, rows.getString(1));
                }
                assertFalse(rows.next());
            }
        }
    }

    @Test
    void schemaScriptAlsoWorksBeforeJpaCreatesTablesInANewDatabase() {
        var database = new DriverManagerDataSource("jdbc:h2:mem:new-schema-" + UUID.randomUUID(), "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(database);
    }
}
