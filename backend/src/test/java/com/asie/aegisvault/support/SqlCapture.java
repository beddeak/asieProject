package com.asie.aegisvault.support;

import java.util.ArrayList;
import java.util.List;
import org.hibernate.resource.jdbc.spi.StatementInspector;

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
