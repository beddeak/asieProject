package com.asie.aegisvault;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class SchemaMigrationTest {
  @Test
  void upgradesLegacyDataAndRunsOnlyOnce() {
    var data =
        new DriverManagerDataSource(
            "jdbc:h2:mem:legacy-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    new ResourceDatabasePopulator(new ClassPathResource("legacy-schema.sql")).execute(data);
    var jdbc = new JdbcTemplate(data);
    jdbc.update("insert into department(id,name,description) values(1,'연구개발','기존 부서')");
    jdbc.update(
        "insert into"
            + " users(id,nickname,email,password,position,account_status,department,created_at)"
            + " values(1,'legacy','legacy@example.test','UNCHANGED_HASH','MANAGER','ACTIVE',1,CURRENT_TIMESTAMP)");
    jdbc.update(
        "insert into document(id,author_id,department_id,created_at)"
            + " values(1,1,1,CURRENT_TIMESTAMP)");
    jdbc.update(
        "insert into"
            + " document_version(id,document_id,version_number,title,content,status,created_at)"
            + " values(1,1,1,'기존 문서','보존할 본문','APPROVED',CURRENT_TIMESTAMP)");
    jdbc.update(
        "insert into"
            + " department_notice(id,department_id,author_id,title,content,version,created_at,updated_at)"
            + " values(1,1,1,'기존 공지','보존할 공지',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    var flyway =
        Flyway.configure().dataSource(data).baselineOnMigrate(true).baselineVersion("0").load();
    assertEquals(2, flyway.migrate().migrationsExecuted);
    assertEquals(0, flyway.migrate().migrationsExecuted);
    assertEquals(
        "UNCHANGED_HASH",
        jdbc.queryForObject("select password from users where id=1", String.class));
    assertEquals(
        "보존할 본문",
        jdbc.queryForObject("select content from document_version where id=1", String.class));
    assertEquals(
        "DEPARTMENT",
        jdbc.queryForObject("select scope from department_notice where id=1", String.class));
    assertEquals(
        "INTERNAL", jdbc.queryForObject("select clearance from users where id=1", String.class));
    assertEquals(0L, jdbc.queryForObject("select revision from document where id=1", Long.class));
    jdbc.update(
        "insert into"
            + " department_notice(id,author_id,title,content,scope,version,created_at,updated_at)"
            + " values(2,1,'전체 공지','내용','GLOBAL',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    assertEquals(2, jdbc.queryForObject("select count(*) from department_notice", Integer.class));
  }

  @Test
  void existingFileDatabaseIsBackedUpBeforeMigration(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
    var data =
        new DriverManagerDataSource(
            "jdbc:h2:file:" + directory.resolve("legacy").toAbsolutePath(), "sa", "");
    new ResourceDatabasePopulator(new ClassPathResource("legacy-schema.sql")).execute(data);
    var flyway =
        Flyway.configure().dataSource(data).baselineOnMigrate(true).baselineVersion("0").load();
    var strategy =
        new com.asie.aegisvault.config.LocalDatabaseMigration()
            .migrationStrategy(
                directory.resolve("backups").toString(),
                directory.resolve("absent.key").toString());
    strategy.migrate(flyway);
    try (var backups = java.nio.file.Files.list(directory.resolve("backups"))) {
      assertEquals(1, backups.filter(p -> p.toString().endsWith(".zip")).count());
    }
    strategy.migrate(flyway);
    try (var backups = java.nio.file.Files.list(directory.resolve("backups"))) {
      assertEquals(1, backups.count());
    }
  }

  @Test
  void rejectsDanglingVersionReferences() {
    var data =
        new DriverManagerDataSource(
            "jdbc:h2:mem:foreign-keys-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    Flyway.configure().dataSource(data).load().migrate();
    var jdbc = new JdbcTemplate(data);
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> jdbc.update("insert into release_item(release_id,version_id) values(999,999)"));
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "insert into notification(recipient_id,kind,message,link,created_at)"
                    + " values(999,'TEST','test','/tasks',CURRENT_TIMESTAMP)"));
  }

  @Test
  void createsNewDatabaseWithVersionedSchema() {
    var data =
        new DriverManagerDataSource(
            "jdbc:h2:mem:fresh-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    var flyway = Flyway.configure().dataSource(data).load();
    assertEquals(2, flyway.migrate().migrationsExecuted);
    assertEquals(0, flyway.migrate().migrationsExecuted);
  }
}
