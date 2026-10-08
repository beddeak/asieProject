package com.asie.aegisvault.project;

import com.asie.aegisvault.Document.DocumentCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRequirementRepository extends JpaRepository<ProjectRequirement, Long> {
  List<ProjectRequirement> findByProjectIdOrderByCategory(Long projectId);

  Optional<ProjectRequirement> findByProjectIdAndCategory(
      Long projectId, DocumentCategory category);
}
