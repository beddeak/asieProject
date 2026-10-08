package com.asie.aegisvault.Document;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DocumentCategory {
  DESIGN("설계 문서"),
  TEST_REPORT("시험 보고서"),
  SECURITY_REVIEW("보안 검토서"),
  OTHER("일반 문서");
  private final String label;
}
