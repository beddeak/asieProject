package com.asie.aegisvault.Document.dto;

import com.asie.aegisvault.Document.DocumentCategory;
import com.asie.aegisvault.Document.DocumentStatus;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.security.SecurityClassification;
import java.time.LocalDateTime;

public record DocumentListItem(
    Long documentId,
    Long versionId,
    String title,
    DocumentStatus status,
    String authorName,
    String departmentName,
    Position requiredPosition,
    int versionNumber,
    LocalDateTime createdAt,
    Long projectId,
    String projectName,
    DocumentCategory category,
    SecurityClassification classification,
    boolean archived) {}
