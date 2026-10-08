package com.asie.aegisvault.workflow;

import com.asie.aegisvault.Document.DocumentVersionRepository;
import com.asie.aegisvault.Document.dto.VersionManifest;
import com.asie.aegisvault.attachment.*;
import com.asie.aegisvault.project.ProjectRequirementRepository;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Call under the project lock when evidence is created or used to release. */
@Component
@RequiredArgsConstructor
public class ProjectEvidence {
  private final DocumentVersionRepository versions;
  private final AttachmentRepository files;
  private final ProjectRequirementRepository requirements;
  private final QualityCheckRepository checks;

  public Snapshot snapshot(Long projectId) {
    List<VersionManifest> documents = versions.currentProjectVersions(projectId);
    List<Attachment> attachments =
        documents.isEmpty()
            ? List.of()
            : files.manifest(documents.stream().map(VersionManifest::versionId).toList());
    StringBuilder canonical = new StringBuilder("project:").append(projectId).append('\n');
    documents.forEach(
        v ->
            canonical
                .append(v.documentId())
                .append(':')
                .append(v.versionId())
                .append(':')
                .append(v.status())
                .append(':')
                .append(v.category())
                .append(':')
                .append(v.classification())
                .append('\n'));
    attachments.forEach(
        a ->
            canonical
                .append("file:")
                .append(a.getVersionId())
                .append(':')
                .append(a.getId())
                .append(':')
                .append(a.getSha256())
                .append('\n'));
    requirements
        .findByProjectIdOrderByCategory(projectId)
        .forEach(
            r ->
                canonical
                    .append("required:")
                    .append(r.getCategory())
                    .append(':')
                    .append(r.getMinimumCount())
                    .append('\n'));
    checks
        .findByProjectIdAndActiveTrueOrderById(projectId)
        .forEach(c -> canonical.append("check:").append(c.getId()).append('\n'));
    try {
      return new Snapshot(
          documents,
          attachments,
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(canonical.toString().getBytes(StandardCharsets.UTF_8))));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public record Snapshot(
      List<VersionManifest> versions, List<Attachment> attachments, String fingerprint) {}
}
