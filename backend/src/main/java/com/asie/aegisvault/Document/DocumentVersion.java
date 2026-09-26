package com.asie.aegisvault.Document;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "document_version", uniqueConstraints = {
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

    @Column(nullable = false, length = 255)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentStatus status;

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
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("문서 제목을 입력해주세요");
        }
        if (title.length() > 255) {
            throw new IllegalArgumentException("문서 제목은 255자 이하여야 합니다");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("문서 본문을 입력해주세요");
        }

        this.document = document;
        this.versionNumber = versionNumber;
        this.title = title;
        this.content = content;
        this.status = DocumentStatus.DRAFT;
    }
}
