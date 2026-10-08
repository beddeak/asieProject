package com.asie.aegisvault.account;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "ix_recovery_user", columnList = "userId,completedAt"))
public class PasswordRecovery {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  private String nickname;
  private Instant requestedAt = Instant.now();

  @Column(unique = true, length = 64)
  private String tokenHash;

  private Instant expiresAt;
  private Instant completedAt;
  private String issuedBy;
  @Lob private String verification;
  @Version private Long revision;

  public PasswordRecovery(Long userId, String nickname) {
    this.userId = userId;
    this.nickname = nickname;
  }

  public void issue(String hash, Instant until, String actor, String verification) {
    if (completedAt != null) throw new IllegalStateException("종료된 요청입니다.");
    if (verification == null || verification.isBlank())
      throw new IllegalArgumentException("신원 확인 근거를 입력해주세요.");
    tokenHash = hash;
    expiresAt = until;
    issuedBy = actor;
    this.verification = verification;
  }

  public void complete() {
    completedAt = Instant.now();
    tokenHash = null;
  }

  public boolean usable() {
    return tokenHash != null && completedAt == null && expiresAt.isAfter(Instant.now());
  }
}
