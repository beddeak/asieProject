package com.asie.aegisvault.Document.dto;

import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.Document.DocumentContentRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DocumentCreateRequest(
    @NotBlank(message = "문서 제목을 입력해주세요")
    @Size(max = DocumentContentRules.MAX_TITLE_LENGTH, message = "문서 제목은 {max}자 이하여야 합니다")
    String title,

    @NotBlank(message = "문서 본문을 입력해주세요")
    @Size(max = DocumentContentRules.MAX_CONTENT_LENGTH, message = "문서 본문은 {max}자 이하여야 합니다")
    String content,

    @NotNull(message = "열람 가능한 최소 직급을 선택해주세요")
    Position requiredPosition
) {
}
