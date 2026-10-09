package com.asie.aegisvault.workflow;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QualityRunRepository extends JpaRepository<QualityRun, Long> {
  Optional<QualityRun> findFirstByProjectIdOrderByIdDesc(Long projectId);

  Page<QualityRun> findByProjectId(Long projectId, Pageable page);

  @Query(
      """
      select new com.asie.aegisvault.workflow.QualityRunOption(
        r.id, r.retestOf, r.testerName, r.createdAt)
      from QualityRun r
      where r.projectId = :projectId and r.passed = true
        and r.fingerprint = :fingerprint and r.retestOf in :failedRunIds
        and r.id = (
          select max(latest.id) from QualityRun latest
          where latest.projectId = :projectId and latest.passed = true
            and latest.fingerprint = :fingerprint and latest.retestOf = r.retestOf
        )
      """)
  List<QualityRunOption> latestPassingRetests(
      @Param("projectId") Long projectId,
      @Param("fingerprint") String fingerprint,
      @Param("failedRunIds") List<Long> failedRunIds);
}
