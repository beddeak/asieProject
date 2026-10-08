package com.asie.aegisvault.project;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProjectStatus {
  PLANNING("준비"),
  ACTIVE("진행"),
  REVIEW("검토"),
  RELEASED("배포"),
  CLOSED("종료");
  private final String label;
}
