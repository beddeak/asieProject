package com.asie.aegisvault.workflow;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NonconformityRepository extends JpaRepository<Nonconformity, Long> {
  Page<Nonconformity> findByProjectId(Long projectId, Pageable page);

  long countByProjectIdAndClosedFalse(Long projectId);
}
