package com.asie.aegisvault.release;

import com.asie.aegisvault.Document.dto.DocumentListItem;
import java.util.List;
import org.springframework.data.domain.Page;

public record ReleaseDetail(
    Release release,
    List<ReleasedDocument> documents,
    Page<ReleaseRecipient> recipients,
    boolean downloadAllowed,
    boolean recallAllowed,
    boolean projectAccessible) {

  public record ReleasedDocument(
      DocumentListItem version, String packagePath, List<ReleasedFile> files) {}

  public record ReleasedFile(
      Long id, String filename, long size, String sha256, String packagePath) {}
}
