package com.asie.aegisvault.activity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentAction {
    CREATED("작성·검토 요청"),
    READ("문서 열람"),
    REVIEW_OPENED("검토 화면 열람"),
    APPROVED("승인"),
    REJECTED("반려");

    private final String displayName;
}
