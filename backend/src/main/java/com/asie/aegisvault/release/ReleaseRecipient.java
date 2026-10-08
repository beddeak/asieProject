package com.asie.aegisvault.release;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_release_recipient",
            columnNames = {"releaseId", "userId"}))
public class ReleaseRecipient {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long releaseId;

  @Column(nullable = false)
  private Long userId;

  private String nickname;

  public ReleaseRecipient(Long releaseId, Long userId, String nickname) {
    this.releaseId = releaseId;
    this.userId = userId;
    this.nickname = nickname;
  }
}
