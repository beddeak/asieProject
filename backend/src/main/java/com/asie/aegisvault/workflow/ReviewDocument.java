package com.asie.aegisvault.workflow;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.*;

/** The submitted document versions that were actually examined in one review. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_review_document_quality",
          columnNames = {"quality_run_id", "version_id"}),
      @UniqueConstraint(
          name = "uk_review_document_security",
          columnNames = {"security_assessment_id", "version_id"})
    })
public class ReviewDocument {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long qualityRunId;
  private Long securityAssessmentId;

  @Column(nullable = false)
  private Long versionId;

  private ReviewDocument(Long qualityRunId, Long securityAssessmentId, Long versionId) {
    if ((qualityRunId == null) == (securityAssessmentId == null))
      throw new IllegalArgumentException("하나의 품질시험 또는 보안 검토에 연결해주세요.");
    this.qualityRunId = qualityRunId;
    this.securityAssessmentId = securityAssessmentId;
    this.versionId = Objects.requireNonNull(versionId, "검토한 문서 버전이 필요합니다.");
  }

  public static ReviewDocument quality(Long runId, Long versionId) {
    return new ReviewDocument(Objects.requireNonNull(runId), null, versionId);
  }

  public static ReviewDocument security(Long assessmentId, Long versionId) {
    return new ReviewDocument(null, Objects.requireNonNull(assessmentId), versionId);
  }
}
