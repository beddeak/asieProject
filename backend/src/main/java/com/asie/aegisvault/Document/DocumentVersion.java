package com.asie.aegisvault.Document;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import com.asie.aegisvault.User.User;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "document_version", indexes = @Index(name = "IDX_document_version_review", columnList = "status, created_at"), uniqueConstraints = {
        @UniqueConstraint(name = "UK_document_version_number", columnNames = {"document_id", "version_number"})
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

    @Column(nullable = false, length = DocumentContentRules.MAX_TITLE_LENGTH)
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

    public DocumentVersion(Document document, int versionNumber, String title, String content) {
        if (document == null) {
            throw new IllegalArgumentException("버전이 속할 문서가 필요합니다");
        }
        if (versionNumber < 1) {
            throw new IllegalArgumentException("버전 번호는 1 이상이어야 합니다");
        }
        DocumentContentRules.validate(title, content);

        this.document = document;
        this.versionNumber = versionNumber;
        this.title = title;
        this.content = content;
        this.status = DocumentStatus.DRAFT;
    }

    public void submitForReview() {
        if (status != DocumentStatus.DRAFT) {
            throw new IllegalStateException("초안만 검토를 요청할 수 있습니다.");
        }
        this.status = DocumentStatus.PENDING_REVIEW;
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
