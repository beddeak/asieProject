package com.asie.aegisvault.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "ix_change_project", columnList = "projectId,status"))
public class EngineeringChange {
  public enum Status {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    REJECTED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long projectId;

  @Column(nullable = false, length = 200)
  private String title;

  @Lob
  @Column(nullable = false)
  private String description;

  private Long requesterId;
  private String requesterName;
  private Long assigneeId;
  private String assigneeName;
  private Long baseVersionId;
  private Long resolvedVersionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status = Status.OPEN;

  @Lob private String resolution;
  private Instant createdAt = Instant.now();
  private Instant resolvedAt;
  @Version private Long revision;

  public EngineeringChange(
      Long projectId,
      String title,
      String description,
      Long baseVersionId,
      com.asie.aegisvault.User.User actor) {
    if (title == null
        || title.isBlank()
        || title.length() > 200
        || description == null
        || description.isBlank())
      throw new IllegalArgumentException("변경 제목(최대 200자)과 요청 내용을 입력해주세요.");
    this.projectId = projectId;
    this.title = title;
    this.description = description;
    this.baseVersionId = baseVersionId;
    requesterId = actor.getId();
    requesterName = actor.getNickname();
  }

  public void assign(com.asie.aegisvault.User.User actor) {
    requireOpen();
    assigneeId = actor.getId();
    assigneeName = actor.getNickname();
    status = Status.IN_PROGRESS;
  }

  public void resolve(Long versionId, String reason, boolean rejected) {
    requireOpen();
    if (reason == null || reason.isBlank()) throw new IllegalArgumentException("처리 사유를 입력해주세요.");
    if (!rejected && versionId == null) throw new IllegalArgumentException("변경을 반영한 새 버전이 필요합니다.");
    resolvedVersionId = versionId;
    resolution = reason;
    status = rejected ? Status.REJECTED : Status.RESOLVED;
    resolvedAt = Instant.now();
  }

  private void requireOpen() {
    if (status == Status.RESOLVED || status == Status.REJECTED)
      throw new IllegalStateException("이미 처리된 변경 요청입니다.");
  }
}
