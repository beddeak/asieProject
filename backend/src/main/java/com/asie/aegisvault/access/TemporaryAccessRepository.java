package com.asie.aegisvault.access;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TemporaryAccessRepository extends JpaRepository<TemporaryAccess, Long> {
  @Query(
      """
      select count(t)>0 from TemporaryAccess t where t.documentId=:documentId and t.userId=:userId
      and t.status=com.asie.aegisvault.access.TemporaryAccess.Status.APPROVED and t.approvedAt<=:now and t.expiresAt>:now
      """)
  boolean effective(
      @Param("documentId") Long documentId,
      @Param("userId") Long userId,
      @Param("now") Instant now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from TemporaryAccess t where t.id=:id")
  Optional<TemporaryAccess> lockById(@Param("id") Long id);

  Page<TemporaryAccess> findByUserId(Long userId, Pageable pageable);

  Page<TemporaryAccess> findByDocumentId(Long documentId, Pageable pageable);

  boolean existsByDocumentIdAndUserIdAndStatus(
      Long documentId, Long userId, TemporaryAccess.Status status);
}
