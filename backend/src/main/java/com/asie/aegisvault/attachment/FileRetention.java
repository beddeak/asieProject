package com.asie.aegisvault.attachment;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FileRetention {
  private static final Logger log = LoggerFactory.getLogger(FileRetention.class);
  private final Path root;
  private final AttachmentRepository attachments;

  public FileRetention(
      @Value("${app.files.path:./local/files}") String root, AttachmentRepository attachments) {
    this.root = Path.of(root).toAbsolutePath().normalize();
    this.attachments = attachments;
  }

  @Scheduled(cron = "${app.files.cleanup-cron:0 30 3 * * *}", zone = "UTC")
  public void clean() throws IOException {
    if (!Files.isDirectory(root)) return;
    Instant cutoff = Instant.now().minus(Duration.ofDays(1));
    int removed = 0;
    try (var files = Files.newDirectoryStream(root)) {
      for (Path file : files) {
        String key = file.getFileName().toString();
        if (key.matches("[0-9a-f-]{36}")
            && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
            && Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS)
                .toInstant()
                .isBefore(cutoff)
            && !attachments.existsByStorageKey(key)) {
          Files.deleteIfExists(file);
          removed++;
        }
      }
    }
    if (removed > 0)
      log.info("Removed {} unreferenced attachment blobs older than 24 hours", removed);
  }
}
