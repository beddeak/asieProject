package com.asie.aegisvault.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "ix_security_assessment_project", columnList = "projectId,id"))
public class SecurityAssessment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long projectId;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  private Long reviewerId;
  private String reviewerName;
  private boolean approved;

  @Lob
  @Column(nullable = false)
  private String findings;

  private Instant createdAt = Instant.now();

  public SecurityAssessment(
      Long projectId,
      String fingerprint,
      com.asie.aegisvault.User.User actor,
      boolean approved,
      String findings) {
    if (findings == null || findings.isBlank())
      throw new IllegalArgumentException("보안 검토 근거와 잔여 위험을 입력해주세요.");
    this.projectId = projectId;
    this.fingerprint = fingerprint;
    reviewerId = actor.getId();
    reviewerName = actor.getNickname();
    this.approved = approved;
    this.findings = findings;
  }
}
