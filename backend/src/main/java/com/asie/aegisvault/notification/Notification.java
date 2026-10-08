package com.asie.aegisvault.notification;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "notification",
    indexes = @Index(name = "idx_notification_recipient", columnList = "recipient_id,read_at,id"))
public class Notification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "recipient_id", nullable = false)
  private Long recipientId;

  @Column(nullable = false, length = 80)
  private String kind;

  @Column(nullable = false, length = 4000)
  private String message;

  @Column(nullable = false, length = 300)
  private String link;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(name = "read_at")
  private Instant readAt;

  public Notification(Long recipientId, String kind, String message, String link) {
    if (link == null || !link.startsWith("/") || link.startsWith("//"))
      throw new IllegalArgumentException("알림 주소가 올바르지 않습니다.");
    this.recipientId = recipientId;
    this.kind = kind;
    this.message = message;
    this.link = link;
    createdAt = Instant.now();
  }

  public void read() {
    if (readAt == null) readAt = Instant.now();
  }
}
