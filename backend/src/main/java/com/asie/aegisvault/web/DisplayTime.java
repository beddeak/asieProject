package com.asie.aegisvault.web;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
