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
    assertEquals(3, flyway.migrate().migrationsExecuted);
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
    assertEquals(3, flyway.migrate().migrationsExecuted);
    assertEquals(0, flyway.migrate().migrationsExecuted);
  }

  @Test
  void upgradesReviewHistoryWithoutInventingDocumentBaselines() {
    var data =
        new DriverManagerDataSource(
            "jdbc:h2:mem:review-upgrade-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    Flyway.configure().dataSource(data).target("2").load().migrate();
    var jdbc = new JdbcTemplate(data);
    jdbc.update("insert into department(id,name,description) values(1,'연구개발','기존 부서')");
    jdbc.update(
        "insert into"
            + " users(id,nickname,email,password,position,account_status,department,created_at)"
            + " values(1,'reviewer','reviewer@example.test','UNCHANGED_HASH','MANAGER','ACTIVE',1,CURRENT_TIMESTAMP)");
    jdbc.update(
        "insert into"
            + " project(id,name,description,department_id,created_by,created_at,revision,status)"
            + " values(1,'기존 프로젝트','보존할 설명',1,1,CURRENT_TIMESTAMP,0,'REVIEW')");
    jdbc.update(
        "insert into document(id,author_id,department_id,project_id,created_at)"
            + " values(1,1,1,1,CURRENT_TIMESTAMP)");
    jdbc.update(
        "insert into"
            + " document_version(id,document_id,version_number,title,content,status,created_at)"
            + " values(1,1,1,'기존 승인본','보존할 본문','APPROVED',CURRENT_TIMESTAMP)");
    jdbc.update(
        "insert into quality_run(id,project_id,tester_id,fingerprint,passed,evidence)"
            + " values(1,1,1,'historical-quality',true,'기존 시험 근거')");
    jdbc.update(
        "insert into security_assessment(id,project_id,reviewer_id,fingerprint,approved,findings)"
            + " values(1,1,1,'historical-security',true,'기존 보안 근거')");
    jdbc.update(
        "insert into"
            + " project_release(id,project_id,release_number,quality_run_id,security_assessment_id,fingerprint,revision)"
            + " values(1,1,1,1,1,'historical-release',0)");
    var flyway = Flyway.configure().dataSource(data).load();
    assertEquals(1, flyway.migrate().migrationsExecuted);
    assertEquals(0, flyway.migrate().migrationsExecuted);
    assertEquals(
        "historical-quality",
        jdbc.queryForObject("select fingerprint from quality_run where id=1", String.class));
    assertEquals(
        "기존 시험 근거",
        jdbc.queryForObject("select evidence from quality_run where id=1", String.class));
    assertEquals(
        "historical-security",
        jdbc.queryForObject(
            "select fingerprint from security_assessment where id=1", String.class));
    assertEquals(
        "기존 보안 근거",
        jdbc.queryForObject("select findings from security_assessment where id=1", String.class));
    assertEquals(
        "historical-release",
        jdbc.queryForObject("select fingerprint from project_release where id=1", String.class));
    assertEquals(0, jdbc.queryForObject("select count(*) from review_document", Integer.class));

    jdbc.update("insert into review_document(quality_run_id,version_id) values(1,1)");
    jdbc.update("insert into review_document(security_assessment_id,version_id) values(1,1)");
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> jdbc.update("insert into review_document(quality_run_id,version_id) values(1,1)"));
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "insert into review_document(security_assessment_id,version_id) values(1,1)"));
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> jdbc.update("insert into review_document(version_id) values(1)"));
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "insert into review_document(quality_run_id,security_assessment_id,version_id)"
                    + " values(1,1,1)"));
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> jdbc.update("insert into review_document(quality_run_id,version_id) values(999,1)"));
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "insert into review_document(security_assessment_id,version_id) values(999,1)"));
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> jdbc.update("insert into review_document(quality_run_id,version_id) values(1,999)"));
  }
}
