package com.asie.aegisvault.Document;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long>{
    @EntityGraph(attributePaths = {"document.author", "document.department"})
    Optional<DocumentVersion> findFirstByDocumentOrderByVersionNumberDesc(Document document);
}
