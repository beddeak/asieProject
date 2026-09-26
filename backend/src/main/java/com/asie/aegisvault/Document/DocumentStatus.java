package com.asie.aegisvault.Document;

public enum DocumentStatus {
    DRAFT,                     // 초안
    PENDING_REVIEW,            // 검토 대기
    PENDING_SECURITY_APPROVAL, // 보안 승인 대기
    APPROVED,                 // 승인 완료
    REJECTED,                 // 반려
    ARCHIVED                  // 보관
}
