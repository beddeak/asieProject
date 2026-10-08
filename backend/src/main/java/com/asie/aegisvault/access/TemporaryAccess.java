package com.asie.aegisvault.access;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "temporary_access",
    indexes =
        @Index(
            name = "idx_temporary_access_effective",
            columnList = "document_id,user_id,status,expires_at"))
public class TemporaryAccess {
  public enum Status {
    REQUESTED,
    APPROVED,
    REJECTED,
    REVOKED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "document_id", nullable = false)
  private Long documentId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 100)
  private String requesterName;

  @Lob
  @Column(nullable = false)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  @Column(nullable = false)
  private Instant requestedAt;

  @Column(nullable = false)
  private Instant requestedUntil;

  private Instant approvedAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  private Long approvedBy;
  @Lob private String decisionReason;
  @Version private Long revision;

  public TemporaryAccess(Long documentId, Long userId, String name, String reason, Instant until) {
    if (reason == null || reason.isBlank()) throw new IllegalArgumentException("임시 접근 사유를 입력해주세요.");
    if (until == null || !until.isAfter(Instant.now()))
      throw new IllegalArgumentException("만료 일시를 미래로 입력해주세요.");
    this.documentId = documentId;
    this.userId = userId;
    this.requesterName = name;
    this.reason = reason;
    this.requestedUntil = until;
    requestedAt = Instant.now();
    status = Status.REQUESTED;
  }

  public void decide(Long reviewer, boolean approved, Instant until, String reason) {
    if (status != Status.REQUESTED) throw new IllegalStateException("대기 중인 요청만 처리할 수 있습니다.");
    if (userId.equals(reviewer)) throw new IllegalArgumentException("자신의 요청은 승인할 수 없습니다.");
    if (reason == null || reason.isBlank()) throw new IllegalArgumentException("처리 사유를 입력해주세요.");
    if (approved
        && (until == null || !until.isAfter(Instant.now()) || until.isAfter(requestedUntil)))
      throw new IllegalArgumentException("승인 만료는 현재 이후, 요청한 만료 일시 이내여야 합니다.");
    status = approved ? Status.APPROVED : Status.REJECTED;
    approvedBy = reviewer;
    approvedAt = Instant.now();
    expiresAt = approved ? until : null;
    decisionReason = reason;
  }

  public void revoke(String reason) {
    if (status != Status.APPROVED && status != Status.REQUESTED)
      throw new IllegalStateException("이미 종료된 요청입니다.");
    if (reason == null || reason.isBlank()) throw new IllegalArgumentException("취소 사유를 입력해주세요.");
    status = Status.REVOKED;
    decisionReason = reason;
  }

  public boolean isExpired() {
    return status == Status.APPROVED && !expiresAt.isAfter(Instant.now());
  }
}
