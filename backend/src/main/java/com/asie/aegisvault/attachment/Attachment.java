package com.asie.aegisvault.attachment;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "ix_attachment_version", columnList = "versionId"))
public class Attachment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long versionId;

  @Column(nullable = false, length = 40)
  private String storageKey;

  @Column(nullable = false, length = 255)
  private String filename;

  @Column(nullable = false, length = 100)
  private String contentType;

  private long size;

  @Column(nullable = false, length = 64)
  private String sha256;

  private Long uploadedBy;
  private Instant uploadedAt;

  public Attachment(
      Long versionId,
      String key,
      String filename,
      String type,
      long size,
      String hash,
      Long actor) {
    this.versionId = versionId;
    storageKey = key;
    this.filename = filename;
    contentType = type;
    this.size = size;
    sha256 = hash;
    uploadedBy = actor;
    uploadedAt = Instant.now();
  }

  public Attachment copy(Long nextVersion) {
    return new Attachment(nextVersion, storageKey, filename, contentType, size, sha256, uploadedBy);
  }
}
