package com.asie.aegisvault.User;

public enum AccountStatus {
    ACTIVE,     // 정상
    BAN,  // 정지
    LOCKED, // 로그인 실패 등으로 잠김
    DELETED // 계정 삭제: 문서와 활동 기록은 보존
}
