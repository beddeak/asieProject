package com.asie.aegisvault.support;

import java.util.ArrayList;
import java.util.List;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/** 검증 구간의 SQL 구조만 수집합니다. 바인딩된 값은 수집하지 않습니다. */
public class SqlCapture implements StatementInspector {
  private static final ThreadLocal<List<String>> CURRENT = new ThreadLocal<>();

  public static List<String> capture(Runnable action) {
    List<String> statements = new ArrayList<>();
    CURRENT.set(statements);
    try {
      action.run();
      return List.copyOf(statements);
    } finally {
      CURRENT.remove();
    }
  }

  @Override
  public String inspect(String sql) {
    List<String> statements = CURRENT.get();
    if (statements != null) {
      statements.add(sql);
    }
    return sql;
  }
}
