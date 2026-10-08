package com.asie.aegisvault.Document;

/** 화면 검증과 도메인 검증에서 함께 사용하는 문서 입력 규칙입니다. */
public final class DocumentContentRules {
    public static final int MAX_TITLE_LENGTH = 255;
    public static final int MAX_CONTENT_LENGTH = 100_000;

    private DocumentContentRules() { }

    public static void validate(String title, String content) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("문서 제목을 입력해주세요");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("문서 제목은 " + MAX_TITLE_LENGTH + "자 이하여야 합니다");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("문서 본문을 입력해주세요");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("문서 본문은 " + MAX_CONTENT_LENGTH + "자 이하여야 합니다");
        }
    }
}
