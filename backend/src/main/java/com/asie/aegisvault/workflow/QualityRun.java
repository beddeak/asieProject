package com.asie.aegisvault.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "ix_quality_run_project", columnList = "projectId,id"))
public class QualityRun {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long projectId;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  private Long testerId;
  private String testerName;
  private boolean passed;

  @Lob
  @Column(nullable = false)
  private String evidence;

  private Long retestOf;
  private Instant createdAt = Instant.now();

  public QualityRun(
      Long projectId,
      String fingerprint,
      com.asie.aegisvault.User.User actor,
      boolean passed,
      String evidence,
      Long retestOf) {
    if (evidence == null || evidence.isBlank())
      throw new IllegalArgumentException("시험 환경, 수행 내용과 결과 근거를 입력해주세요.");
    this.projectId = projectId;
    this.fingerprint = fingerprint;
    testerId = actor.getId();
    testerName = actor.getNickname();
    this.passed = passed;
    this.evidence = evidence;
    this.retestOf = retestOf;
  }
}
