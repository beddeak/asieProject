package com.asie.aegisvault.release;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseItemRepository extends JpaRepository<ReleaseItem, Long> {
  List<ReleaseItem> findByReleaseIdOrderById(Long releaseId);
}
