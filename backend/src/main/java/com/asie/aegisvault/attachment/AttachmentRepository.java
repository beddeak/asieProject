package com.asie.aegisvault.attachment;

import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
  Page<Attachment> findByVersionId(Long versionId, Pageable page);

  List<Attachment> findByVersionIdOrderById(Long versionId);

  boolean existsByStorageKey(String storageKey);

  @Query("select a from Attachment a where a.versionId in :versions order by a.versionId,a.id")
  List<Attachment> manifest(@Param("versions") List<Long> versions);
}
