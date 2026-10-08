package com.asie.aegisvault.Document;

import com.asie.aegisvault.User.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@NoArgsConstructor
@Table(
    name = "document_version",
    indexes = @Index(name = "IDX_document_version_review", columnList = "status, created_at"),
    uniqueConstraints = {
      @UniqueConstraint(
          name = "UK_document_version_number",
          columnNames = {"document_id", "version_number"})
    })
public class DocumentVersion {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "document_id", nullable = false)
  private Document document;

  @Column(name = "version_number", nullable = false)
  private int versionNumber;

  @Column(nullable = false, length = 255)
  private String title;

  @Lob
  @Column(nullable = false)
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private DocumentStatus status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reviewed_by_id")
  private User reviewedBy;

  @Column(name = "reviewed_at")
  private LocalDateTime reviewedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Version
  @ColumnDefault("0")
  private Long revision;

  @Column(name = "editor_id")
  private Long editorId;

  @Column(length = 100)
  private String editorName;

  @Column(name = "security_reviewer_id")
  private Long securityReviewerId;

  @Column(length = 100)
  private String securityReviewerName;

  private LocalDateTime securityReviewedAt;

  @Enumerated(EnumType.STRING)
  @Column(length = 30)
  private DocumentStatus archivedFrom;

  public DocumentVersion(Document document, int versionNumber, String title, String content) {
    this(document, versionNumber, title, content, false);
  }

  private DocumentVersion(
      Document document, int versionNumber, String title, String content, boolean draft) {
    if (document == null) {
      throw new IllegalArgumentException("버전이 속할 문서가 필요합니다");
    }
    if (versionNumber < 1) {
      throw new IllegalArgumentException("버전 번호는 1 이상이어야 합니다");
    }
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("문서 제목을 입력해주세요");
    }
    if (title.length() > 255) {
      throw new IllegalArgumentException("문서 제목은 255자 이하여야 합니다");
    }
    if (!draft && (content == null || content.isBlank())) {
      throw new IllegalArgumentException("문서 본문을 입력해주세요");
    }

    this.document = document;
    this.versionNumber = versionNumber;
    this.title = title;
    this.content = content == null ? "" : content;
    this.status = DocumentStatus.DRAFT;
  }

  public void submitForReview() {
    if (status != DocumentStatus.DRAFT) {
      throw new IllegalStateException("초안만 검토를 요청할 수 있습니다.");
    }
    if (content == null || content.isBlank())
      throw new IllegalArgumentException("검토 요청 전 문서 본문을 입력해주세요.");
    this.status = DocumentStatus.PENDING_REVIEW;
  }

  public static DocumentVersion draft(
      Document document, int number, String title, String content, User editor) {
    DocumentVersion version = new DocumentVersion(document, number, title, content, true);
    version.editorId = editor.getId();
    version.editorName = editor.getNickname();
    return version;
  }

  public void editDraft(String title, String content, User editor) {
    if (status != DocumentStatus.DRAFT)
      throw new IllegalStateException("초안만 수정할 수 있습니다. 새 버전을 만들어주세요.");
    if (title == null || title.isBlank() || title.length() > 255)
      throw new IllegalArgumentException("문서 제목을 입력해주세요. 최대 255자입니다.");
    this.title = title;
    this.content = content == null ? "" : content;
    this.editorId = editor.getId();
    this.editorName = editor.getNickname();
  }

  public boolean writtenBy(Long userId) {
    return java.util.Objects.equals(
        editorId == null ? document.getAuthor().getId() : editorId, userId);
  }

  public void sendToSecurity(User reviewer) {
    completeReview(reviewer, DocumentStatus.PENDING_SECURITY_APPROVAL);
  }

  public void securityDecision(User reviewer, boolean approved) {
    if (status != DocumentStatus.PENDING_SECURITY_APPROVAL)
      throw new IllegalStateException("보안 승인 대기 상태에서만 처리할 수 있습니다.");
    securityReviewerId = reviewer.getId();
    securityReviewerName = reviewer.getNickname();
    securityReviewedAt = LocalDateTime.now();
    status = approved ? DocumentStatus.APPROVED : DocumentStatus.REJECTED;
  }

  public void archive() {
    if (status == DocumentStatus.PENDING_REVIEW
        || status == DocumentStatus.PENDING_SECURITY_APPROVAL)
      throw new IllegalStateException("검토 중인 문서는 검토를 완료한 뒤 보관해주세요.");
    archivedFrom = status;
    status = DocumentStatus.ARCHIVED;
  }

  public void approve(User reviewer) {
    completeReview(reviewer, DocumentStatus.APPROVED);
  }

  public void reject(User reviewer) {
    completeReview(reviewer, DocumentStatus.REJECTED);
  }

  private void completeReview(User reviewer, DocumentStatus result) {
    if (reviewer == null) {
      throw new IllegalArgumentException("검토자가 필요합니다.");
    }
    if (status != DocumentStatus.PENDING_REVIEW) {
      throw new IllegalStateException("검토 대기 중인 문서만 처리할 수 있습니다.");
    }
    this.status = result;
    this.reviewedBy = reviewer;
    this.reviewedAt = LocalDateTime.now();
  }
}
