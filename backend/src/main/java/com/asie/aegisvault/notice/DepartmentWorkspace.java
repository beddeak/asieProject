package com.asie.aegisvault.notice;

import java.util.List;
import org.springframework.data.domain.Page;

public record DepartmentWorkspace(
    List<Tab> tabs,
    Tab selected,
    String description,
    Page<NoticeSummary> notices,
    boolean canManage,
    boolean admin) {
  public record Tab(Long id, String name) {}
}
