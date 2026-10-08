package com.asie.aegisvault.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "ix_nonconformity_project", columnList = "projectId,closed"))
public class Nonconformity {
  @Version private Long revision;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long projectId;

  private Long failedRunId;
  private Long passingRunId;
  @Lob private String description;
  @Lob private String correctiveAction;
  private boolean closed;
  private String closedBy;
  private Instant closedAt;
  private Instant createdAt = Instant.now();

  public Nonconformity(Long projectId, Long failedRunId, String description) {
    this.projectId = projectId;
    this.failedRunId = failedRunId;
    this.description = description;
  }

  public void close(Long passingRunId, String action, String actor) {
    if (closed) throw new IllegalStateException("이미 해결된 부적합입니다.");
    if (action == null || action.isBlank())
      throw new IllegalArgumentException("원인과 시정 조치를 입력해주세요.");
    this.passingRunId = passingRunId;
    correctiveAction = action;
    closed = true;
    closedBy = actor;
    closedAt = Instant.now();
  }
}
