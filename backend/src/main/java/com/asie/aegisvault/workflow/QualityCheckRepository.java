package com.asie.aegisvault.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QualityCheckRepository extends JpaRepository<QualityCheck, Long> {
  List<QualityCheck> findByProjectIdAndActiveTrueOrderById(Long projectId);
}
