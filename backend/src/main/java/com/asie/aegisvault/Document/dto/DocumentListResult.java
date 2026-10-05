package com.asie.aegisvault.Document.dto;

import org.springframework.data.domain.Page;

public record DocumentListResult(Page<DocumentListItem> documents, String departmentName,
                                 boolean departmentRequired, boolean canWrite, boolean canReview) {
}
