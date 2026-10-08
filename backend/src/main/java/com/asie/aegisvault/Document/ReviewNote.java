package com.asie.aegisvault.Document;

import com.asie.aegisvault.User.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "review_note",
    indexes = @Index(name = "idx_review_note_version", columnList = "version_id,id"))
public class ReviewNote {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "version_id", nullable = false)
  private DocumentVersion version;

  @Column(nullable = false)
  private Long authorId;

  @Column(nullable = false, length = 100)
  private String authorName;

  @Column(nullable = false, length = 30)
  private String decision;

  @Lob
  @Column(nullable = false)
  private String content;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  public ReviewNote(DocumentVersion version, User author, String decision, String content) {
    this.version = version;
    this.authorId = author.getId();
    this.authorName = author.getNickname();
    this.decision = decision;
    this.content = content == null ? "" : content;
    this.createdAt = LocalDateTime.now();
  }
}
