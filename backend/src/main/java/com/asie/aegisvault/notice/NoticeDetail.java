package com.asie.aegisvault.notice;

import java.time.LocalDateTime;

public record NoticeDetail(Long id, Long departmentId, String departmentName, String title,
                           String content, String authorName, LocalDateTime createdAt,
                           LocalDateTime updatedAt, Long version, boolean canManage) {
}
