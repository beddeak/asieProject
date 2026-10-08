package com.asie.aegisvault.audit;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface AuditHeadRepository extends JpaRepository<AuditHead, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select h from AuditHead h where h.id=1")
  Optional<AuditHead> lockHead();
}
