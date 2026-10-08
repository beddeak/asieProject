package com.asie.aegisvault.workflow;

import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QualityRunRepository extends JpaRepository<QualityRun, Long> {
  Optional<QualityRun> findFirstByProjectIdOrderByIdDesc(Long projectId);

  Page<QualityRun> findByProjectId(Long projectId, Pageable page);
}
