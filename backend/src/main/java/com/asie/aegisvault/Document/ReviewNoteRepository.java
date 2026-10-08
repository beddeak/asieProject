package com.asie.aegisvault.Document;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewNoteRepository extends JpaRepository<ReviewNote, Long> {
  Page<ReviewNote> findByVersionId(Long versionId, Pageable pageable);
}
