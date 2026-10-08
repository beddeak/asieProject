package com.asie.aegisvault.account;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
  private PasswordPolicy() {}

  public static void validate(String password) {
    if (password == null || password.isBlank() || password.codePointCount(0, password.length()) < 8)
      throw new IllegalArgumentException("비밀번호는 8자 이상 입력해주세요.");
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException("비밀번호는 UTF-8 기준 72바이트 이내로 입력해주세요.");
  }
}
