package com.asie.aegisvault.project;

import java.time.LocalDate;
import java.util.List;

public record ProjectDetail(
    Long id,
    String name,
    String description,
    String departmentName,
    Long departmentId,
    ProjectStatus status,
    LocalDate targetDate,
    Long revision,
    List<Requirement> requirements,
    boolean canManage,
    boolean canWrite,
    boolean canEngineer,
    boolean canQuality,
    boolean canSecurity) {
  public record Requirement(
      com.asie.aegisvault.Document.DocumentCategory category, int minimumCount) {}
}
