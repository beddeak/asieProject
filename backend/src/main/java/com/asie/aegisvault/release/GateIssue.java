package com.asie.aegisvault.release;

import com.asie.aegisvault.project.ProjectProgress;

/** Stable cause and destination; UI never infers workflow state by parsing error messages. */
public record GateIssue(
    ProjectProgress.Key stage, Code code, String message, String actionLabel, String href) {
  public enum Code {
    PROJECT_STATUS,
    CLOSED_DEPARTMENT,
    OWNER_REQUIRED,
    NO_DOCUMENTS,
    PENDING_DOCUMENTS,
    REQUIRED_DOCUMENTS,
    OPEN_CHANGES,
    OPEN_DEFECTS,
    CHECKLIST_REQUIRED,
    QUALITY_REVIEW,
    SECURITY_REVIEW,
    REVIEW_INDEPENDENCE,
    ATTACHMENT_INTEGRITY
  }
}
