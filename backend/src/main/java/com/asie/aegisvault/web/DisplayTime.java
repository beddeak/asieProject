package com.asie.aegisvault.web;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 시간대 없는 기존 문서 시각은 저장 서버의 시간대로 해석합니다. */
@Component("displayTime")
public class DisplayTime {
  private static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));
  private final ZoneId legacyZone;

  public DisplayTime(@Value("${app.display.legacy-zone:}") String legacyZone) {
    this.legacyZone = legacyZone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(legacyZone);
  }

  public String format(TemporalAccessor value) {
    if (value == null) return "—";
    return FORMAT.format(value instanceof LocalDateTime date ? date.atZone(legacyZone) : value);
  }
}
