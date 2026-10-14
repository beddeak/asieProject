package com.asie.aegisvault.Document.dto;

import com.asie.aegisvault.User.Position;
import java.time.LocalDateTime;

public record ReviewQueueItem(
    Long id,
    Long documentId,
    String title,
    int versionNumber,
    String departmentName,
    String authorName,
    Position requiredPosition,
    LocalDateTime createdAt) {}
