package com.asie.aegisvault.Document.dto;

import com.asie.aegisvault.Document.*;
import com.asie.aegisvault.security.SecurityClassification;

public record VersionManifest(
    Long documentId,
    Long versionId,
    int number,
    DocumentStatus status,
    DocumentCategory category,
    SecurityClassification classification,
    Long authorId,
    Long editorId) {}
