package com.asie.aegisvault.project;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProjectSummary(
    Long id,
    String name,
    String departmentName,
    ProjectStatus status,
    LocalDate targetDate,
    LocalDateTime createdAt) {}
