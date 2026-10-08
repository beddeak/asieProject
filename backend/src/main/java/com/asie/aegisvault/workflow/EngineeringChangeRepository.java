package com.asie.aegisvault.workflow;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface EngineeringChangeRepository extends JpaRepository<EngineeringChange, Long> {
  Page<EngineeringChange> findByProjectId(Long projectId, Pageable page);

  long countByProjectIdAndStatusIn(Long projectId, Collection<EngineeringChange.Status> statuses);
}
