package com.asie.aegisvault.config;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.*;

/** H2 BACKUP produces a consistent archive before an existing local database is upgraded. */
@Configuration
public class LocalDatabaseMigration {
  @Bean
  public FlywayMigrationStrategy migrationStrategy(
      @Value("${app.backup.path:./local/backups}") String directory,
      @Value("${app.audit.key-file:./local/audit.key}") String keyFile) {
    return flyway -> {
      if (flyway.info().pending().length > 0) {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
          String url = connection.getMetaData().getURL();
          if (url.startsWith("jdbc:h2:")
              && !url.startsWith("jdbc:h2:mem:")
              && !url.startsWith("jdbc:h2:tcp:")
              && !url.startsWith("jdbc:h2:ssl:")) {
            try (var tables =
                connection
                    .getMetaData()
                    .getTables(
                        null, connection.getSchema(), "%", new String[] {"TABLE", "BASE TABLE"})) {
              if (tables.next()) {
                Path root = Path.of(directory).toAbsolutePath().normalize();
                Files.createDirectories(root);
                boolean posix = Files.getFileStore(root).supportsFileAttributeView("posix");
                if (posix)
                  Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
                String stem =
                    "before-migration-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
                Path target = root.resolve(stem + ".zip");
                try (var backup = connection.prepareStatement("BACKUP TO ?")) {
                  backup.setString(1, target.toString());
                  backup.execute();
                }
                if (posix)
                  Files.setPosixFilePermissions(
                      target, PosixFilePermissions.fromString("rw-------"));
                Path key = Path.of(keyFile);
                if (Files.exists(key)) Files.copy(key, root.resolve(stem + ".audit-key"));
                org.slf4j.LoggerFactory.getLogger(LocalDatabaseMigration.class)
                    .info("Saved database backup before migration: {}", target);
              }
            }
          }
        } catch (Exception e) {
          throw new IllegalStateException("DB 갱신 전 백업을 만들 수 없습니다. 저장 경로와 파일 권한을 확인해주세요.", e);
        }
      }
      flyway.migrate();
    };
  }
}
