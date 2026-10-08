package com.asie.aegisvault.audit;

import com.asie.aegisvault.notification.Notification;
import com.asie.aegisvault.notification.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class BusinessEventListener {
  private final AuditChain audit;
  private final NotificationRepository notifications;

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void record(AuditEvent event) {
    audit.append(event);
    if (event.link() != null)
      notifications.saveAll(
          event.recipients().stream()
              .distinct()
              .map(id -> new Notification(id, event.action(), event.description(), event.link()))
              .toList());
  }
}
