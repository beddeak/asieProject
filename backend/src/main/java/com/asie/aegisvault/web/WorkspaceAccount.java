package com.asie.aegisvault.web;

import com.asie.aegisvault.notification.NotificationRepository;
import com.asie.aegisvault.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceAccount {
  private final CurrentUser actors;
  private final NotificationRepository notifications;

  @Transactional(readOnly = true)
  public View view(String nickname) {
    var user = actors.get(nickname);
    return new View(
        user.getId(),
        user.getNickname(),
        user.getPosition().getDisplayName(),
        user.getPosition().isAdmin(),
        user.getDepartment() == null ? "미배정" : user.getDepartment().getName(),
        user.getClearance().getLabel(),
        notifications.countByRecipientIdAndReadAtIsNull(user.getId()));
  }

  public record View(
      Long id,
      String name,
      String position,
      boolean admin,
      String department,
      String clearance,
      long unread) {}
}
