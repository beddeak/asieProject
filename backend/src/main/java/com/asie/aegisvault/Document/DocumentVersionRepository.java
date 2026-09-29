package com.asie.aegisvault.Document;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long>{
    Optional<DocumentVersion> findFirstByDocumentOrderByVersionNumberDesc(Document document);
}
