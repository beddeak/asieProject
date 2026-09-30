package com.asie.aegisvault.activity;

import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.Document.DocumentVersion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 활동 시점의 정보를 보존합니다. 목록 조회에는 사용자/문서 본문을 불러오지 않습니다. */
@Entity
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Table(name = "document_activity", indexes = {
        @Index(name = "IDX_activity_time", columnList = "occurred_at, id"),
        @Index(name = "IDX_activity_actor", columnList = "actor_id, occurred_at"),
        @Index(name = "IDX_activity_document", columnList = "document_id, occurred_at")
})
public class DocumentActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "actor_nickname", nullable = false, length = 100)
    private String actorNickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_position", nullable = false, length = 30)
    private Position actorPosition;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "document_title", nullable = false, length = 255)
    private String documentTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentAction action;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    public DocumentActivity(User actor, DocumentVersion version, DocumentAction action) {
        this.actorId = actor.getId();
        this.actorNickname = actor.getNickname();
        this.actorPosition = actor.getPosition();
        this.documentId = version.getDocument().getId();
        this.versionId = version.getId();
        this.versionNumber = version.getVersionNumber();
        this.documentTitle = version.getTitle();
        this.action = action;
        this.occurredAt = LocalDateTime.now();
    }
}
