package com.asie.aegisvault.release;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_release_version",
            columnNames = {"releaseId", "versionId"}))
public class ReleaseItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long releaseId;

  @Column(nullable = false)
  private Long versionId;

  public ReleaseItem(Long releaseId, Long versionId) {
    this.releaseId = releaseId;
    this.versionId = versionId;
  }
}
