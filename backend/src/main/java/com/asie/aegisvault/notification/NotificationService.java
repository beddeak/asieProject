package com.asie.aegisvault.notification;

import com.asie.aegisvault.common.PageQueries;
import com.asie.aegisvault.security.CurrentUser;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {
  private final CurrentUser actors;
  private final NotificationRepository notifications;

  public Page<Notification> list(String actor, boolean unread, int page) {
    Long id = actors.get(actor).getId();
    return PageQueries.fetch(
        page, 20, Sort.by(Sort.Direction.DESC, "id"), p -> notifications.list(id, unread, p));
  }

  @Transactional
  public String read(String actor, Long id) {
    var notification =
        notifications
            .findByIdAndRecipientId(id, actors.get(actor).getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    notification.read();
    return notification.getLink();
  }

  @Transactional
  public void readAll(String actor) {
    notifications.readAll(actors.get(actor).getId(), Instant.now());
  }
}
