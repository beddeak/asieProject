package com.asie.aegisvault.Document.dto;

import com.asie.aegisvault.User.Position;

import java.time.LocalDateTime;

/** 검토 목록에 필요한 요약만 조회합니다. 문서 본문과 사용자 엔티티를 포함하지 않습니다. */
public record ReviewQueueItem(Long id, Long documentId, String title, int versionNumber,
                              String departmentName, String authorName, Position requiredPosition,
                              LocalDateTime createdAt) { }
