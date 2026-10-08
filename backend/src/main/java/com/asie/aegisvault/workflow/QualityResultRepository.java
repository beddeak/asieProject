package com.asie.aegisvault.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QualityResultRepository extends JpaRepository<QualityResult, Long> {
  List<QualityResult> findByRunIdOrderById(Long runId);
}
