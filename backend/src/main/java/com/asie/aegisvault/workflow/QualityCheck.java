package com.asie.aegisvault.workflow;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "ix_quality_check_project", columnList = "projectId,active"))
public class QualityCheck {
  @Version private Long revision;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long projectId;

  @Column(nullable = false, length = 300)
  private String criterion;

  private boolean active = true;

  public QualityCheck(Long projectId, String criterion) {
    if (criterion == null || criterion.isBlank() || criterion.length() > 300)
      throw new IllegalArgumentException("시험 기준을 300자 이내로 입력해주세요.");
    this.projectId = projectId;
    this.criterion = criterion;
  }

  public void retire() {
    active = false;
  }
}
