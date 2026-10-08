package com.asie.aegisvault.release;

import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseRecipientRepository extends JpaRepository<ReleaseRecipient, Long> {
  boolean existsByReleaseIdAndUserId(Long releaseId, Long userId);

  List<ReleaseRecipient> findByReleaseId(Long releaseId);

  Page<ReleaseRecipient> findByReleaseId(Long releaseId, Pageable page);
}
