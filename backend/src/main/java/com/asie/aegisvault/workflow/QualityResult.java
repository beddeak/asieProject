package com.asie.aegisvault.workflow;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_quality_result",
            columnNames = {"runId", "checkId"}))
public class QualityResult {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long runId;

  @Column(nullable = false)
  private Long checkId;

  @Column(nullable = false, length = 300)
  private String criterion;

  private boolean passed;

  public QualityResult(Long runId, QualityCheck check, boolean passed) {
    this.runId = runId;
    checkId = check.getId();
    criterion = check.getCriterion();
    this.passed = passed;
  }
}
