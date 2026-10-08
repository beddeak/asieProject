package com.asie.aegisvault.common;

import java.util.Locale;

public final class SearchText {
  private SearchText() {}

  public static String contains(String value) {
    return value == null || value.isBlank()
        ? null
        : "%"
            + value
                .strip()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            + "%";
  }
}
