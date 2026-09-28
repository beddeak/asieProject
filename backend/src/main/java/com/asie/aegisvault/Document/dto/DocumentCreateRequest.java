package com.asie.aegisvault.Document.dto;

import com.asie.aegisvault.Document.DocumentStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DocumentCreateRequest(
    @NotBlank(message = "문서 제목을 입력해주세요")
    @Size(max = 50, message = "문서 제목은 50자 이하여야 합니다")
    String title,

    @NotBlank(message = "문서 본문을 입력해주세요")
    String content,

    @NotNull(message = "버전 번호를 입력해주세요")
    @Min(value = 1, message = "버전 번호는 1 이상이어야 합니다")
    Integer versionNumber,

    @NotNull(message = "문서 상태를 선택해주세요")
    DocumentStatus status
) {
}
