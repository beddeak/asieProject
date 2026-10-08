package com.asie.aegisvault.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SecurityClassification {
  INTERNAL("사내", 0),
  CONFIDENTIAL("기밀", 1),
  RESTRICTED("제한", 2);
  private final String label;
  private final int level;

  public boolean permits(SecurityClassification required) {
    return level >= required.level;
  }
}
