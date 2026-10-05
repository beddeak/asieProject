package com.asie.aegisvault.Document.dto;

import com.asie.aegisvault.Document.DocumentStatus;
import com.asie.aegisvault.User.Position;

import java.time.LocalDateTime;

public record DocumentListItem(Long documentId, String title, DocumentStatus status,
                               String authorName, String departmentName, Position requiredPosition,
                               int versionNumber, LocalDateTime createdAt) {
}
