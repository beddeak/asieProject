package com.asie.aegisvault.notice;

import java.time.LocalDateTime;

public record NoticeSummary(Long id, String title, String authorName,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
}
