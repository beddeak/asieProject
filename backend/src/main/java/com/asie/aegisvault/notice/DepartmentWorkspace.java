package com.asie.aegisvault.notice;

import org.springframework.data.domain.Page;

import java.util.List;

public record DepartmentWorkspace(List<Tab> tabs, Tab selected, String description,
                                   Page<NoticeSummary> notices, boolean canManage, boolean admin) {
    public record Tab(Long id, String name) {
    }
}
