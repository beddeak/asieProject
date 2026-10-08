package com.asie.aegisvault.release;

import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseRepository extends JpaRepository<Release, Long> {
  Page<Release> findByProjectId(Long projectId, Pageable page);

  Optional<Release> findFirstByProjectIdOrderByReleaseNumberDesc(Long projectId);
  @org.springframework.data.jpa.repository.Query("select new com.asie.aegisvault.release.ReleaseSummary(r.id,r.releaseNumber,r.releasedBy,r.releasedAt,r.recalledAt) from Release r where r.projectId=:projectId")
  Page<ReleaseSummary> summaries(@org.springframework.data.repository.query.Param("projectId") Long projectId, Pageable pageable);
}
