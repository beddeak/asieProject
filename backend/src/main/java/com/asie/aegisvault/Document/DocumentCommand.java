package com.asie.aegisvault.Document;

import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.security.SecurityClassification;

public record DocumentCommand(
    String title,
    String content,
    Position requiredPosition,
    Long projectId,
    DocumentCategory category,
    SecurityClassification classification,
    boolean draft) {}
