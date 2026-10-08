package com.asie.aegisvault.project;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProjectRole {
  OWNER("책임자"),
  ENGINEERING("연구개발"),
  QUALITY("품질"),
  SECURITY("보안"),
  CONTRIBUTOR("작성자"),
  VIEWER("열람자");
  private final String label;

  public boolean canWrite() {
    return this == OWNER || this == ENGINEERING || this == CONTRIBUTOR;
  }
}
