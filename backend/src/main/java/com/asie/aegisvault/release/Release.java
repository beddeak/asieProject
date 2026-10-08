package com.asie.aegisvault.release;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "project_release",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_project_release_number",
            columnNames = {"projectId", "releaseNumber"}))
public class Release {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long projectId;

  private int releaseNumber;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  private Long qualityRunId;
  private Long securityAssessmentId;
  private String releasedBy;
  private Instant releasedAt = Instant.now();
  @Lob private String notes;
  private Instant recalledAt;
  private String recalledBy;
  @Lob private String recallReason;
  @Version private Long revision;

  public Release(
      Long projectId,
      int number,
      String fingerprint,
      Long qualityId,
      Long securityId,
      String actor,
      String notes) {
    this.projectId = projectId;
    releaseNumber = number;
    this.fingerprint = fingerprint;
    qualityRunId = qualityId;
    securityAssessmentId = securityId;
    releasedBy = actor;
    this.notes = notes;
  }

  public void recall(String actor, String reason) {
    if (recalledAt != null) throw new IllegalStateException("이미 회수된 배포입니다.");
    if (reason == null || reason.isBlank()) throw new IllegalArgumentException("회수 사유를 입력해주세요.");
    recalledAt = Instant.now();
    recalledBy = actor;
    recallReason = reason;
  }
}
