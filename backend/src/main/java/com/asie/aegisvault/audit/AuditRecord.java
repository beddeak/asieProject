package com.asie.aegisvault.audit;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "audit_record",
    indexes = {
      @Index(name = "idx_audit_project", columnList = "project_id,id"),
      @Index(name = "idx_audit_actor", columnList = "actor,id"),
      @Index(name = "idx_audit_action", columnList = "action,id")
    })
public class AuditRecord {
  @Id private Long id;

  @Column(nullable = false, length = 100, updatable = false)
  private String actor;

  @Column(nullable = false, length = 80, updatable = false)
  private String action;

  @Column(nullable = false, length = 40, updatable = false)
  private String targetType;

  @Column(updatable = false)
  private Long targetId;

  @Column(name = "project_id", updatable = false)
  private Long projectId;

  @Column(nullable = false, length = 4000, updatable = false)
  private String description;

  @Column(nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(nullable = false, length = 64, updatable = false)
  private String previousHash;

  @Column(nullable = false, length = 64, updatable = false)
  private String recordHash;

  public AuditRecord(long id, AuditEvent event, Instant time, String previousHash) {
    this.id = id;
    this.actor = event.actor();
    this.action = event.action();
    this.targetType = event.targetType();
    this.targetId = event.targetId();
    this.projectId = event.projectId();
    this.description = event.description();
    this.occurredAt = time;
    this.previousHash = previousHash;
  }

  void sign(String hash) {
    if (recordHash != null) throw new IllegalStateException("이미 서명된 기록입니다.");
    recordHash = hash;
  }
}
