package com.asie.aegisvault.audit;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "audit_head")
public class AuditHead {
  @Id private Long id;

  @Column(nullable = false)
  private long sequence;

  @Column(nullable = false, length = 64)
  private String recordHash;

  public static AuditHead initial() {
    AuditHead head = new AuditHead();
    head.id = 1L;
    head.sequence = 0;
    head.recordHash = "0".repeat(64);
    return head;
  }

  void advance(AuditRecord record) {
    sequence = record.getId();
    recordHash = record.getRecordHash();
  }
}
