package com.asie.aegisvault.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DisplayTimeTest {
  @Test
  void instantAlwaysUsesKoreanTimeIncludingDayRollover() {
    assertEquals(
        "2026.10.10 00:30", new DisplayTime("UTC").format(Instant.parse("2026-10-09T15:30:00Z")));
  }

  @Test
  void legacyDatesRespectTheOriginalStorageZone() {
    LocalDateTime value = LocalDateTime.of(2026, 10, 9, 15, 30);
    assertEquals("2026.10.10 00:30", new DisplayTime("UTC").format(value));
    assertEquals("2026.10.09 15:30", new DisplayTime("Asia/Seoul").format(value));
    assertEquals("—", new DisplayTime("UTC").format(null));
  }
}
