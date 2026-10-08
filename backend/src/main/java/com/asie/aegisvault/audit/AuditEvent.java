package com.asie.aegisvault.audit;

import com.asie.aegisvault.User.User;
import java.util.List;

public record AuditEvent(
    String actor,
    String action,
    String targetType,
    Long targetId,
    Long projectId,
    String description,
    List<Long> recipients,
    String link) {
  public static AuditEvent of(
      User actor, String action, String type, Long id, Long projectId, String description) {
    return new AuditEvent(
        actor.getNickname(), action, type, id, projectId, description, List.of(), null);
  }

  public AuditEvent notify(List<Long> recipients, String link) {
    return new AuditEvent(
        actor, action, targetType, targetId, projectId, description, List.copyOf(recipients), link);
  }
}
