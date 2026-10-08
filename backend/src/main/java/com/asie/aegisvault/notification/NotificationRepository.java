package com.asie.aegisvault.notification;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

  @Query(
      "select n from Notification n where n.recipientId=:userId and (:unread=false or n.readAt is"
          + " null)")
  Page<Notification> list(
      @Param("userId") Long userId, @Param("unread") boolean unread, Pageable pageable);

  long countByRecipientIdAndReadAtIsNull(Long recipientId);

  @Modifying
  @Query("update Notification n set n.readAt=:now where n.recipientId=:userId and n.readAt is null")
  int readAll(@Param("userId") Long userId, @Param("now") Instant now);
}
