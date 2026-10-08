package com.asie.aegisvault.notice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NoticeForm {
  @NotBlank(message = "공지 제목을 입력해주세요.")
  @Size(max = 255, message = "공지 제목은 255자 이하여야 합니다.")
  private String title;

  @NotBlank(message = "공지 내용을 입력해주세요.")
  @Size(max = 10000, message = "공지 내용은 10,000자 이하여야 합니다.")
  private String content;

  private Long version;
}
