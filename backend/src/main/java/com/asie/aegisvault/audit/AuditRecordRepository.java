package com.asie.aegisvault.audit;

import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {
  @Query(
      """
      select a from AuditRecord a where (:projectId is null or a.projectId=:projectId)
      and (:actor is null or a.actor=:actor) and (:action is null or a.action=:action)
      """)
  Page<AuditRecord> search(
      @Param("projectId") Long projectId,
      @Param("actor") String actor,
      @Param("action") String action,
      Pageable pageable);

  List<AuditRecord> findByIdGreaterThanAndIdLessThanEqualOrderById(
      long after, long through, Pageable pageable);
}
