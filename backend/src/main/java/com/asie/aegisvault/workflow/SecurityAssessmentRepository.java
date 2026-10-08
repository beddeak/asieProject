package com.asie.aegisvault.workflow;

import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAssessmentRepository extends JpaRepository<SecurityAssessment, Long> {
  Optional<SecurityAssessment> findFirstByProjectIdOrderByIdDesc(Long projectId);

  Page<SecurityAssessment> findByProjectId(Long projectId, Pageable page);
}
