package com.asie.aegisvault.Document;

import java.util.Optional;
import java.util.List;

import com.asie.aegisvault.User.Position;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long>{
    @EntityGraph(attributePaths = {"document.author", "document.department", "reviewedBy"})
    Optional<DocumentVersion> findFirstByDocumentOrderByVersionNumberDesc(Document document);

    @EntityGraph(attributePaths = {"document.author", "document.department", "reviewedBy"})
    Optional<DocumentVersion> findForDisplayById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from DocumentVersion v where v.id = :id")
    Optional<DocumentVersion> findForReviewById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"document.author", "document.department"})
    @Query("""
            select v from DocumentVersion v
            where v.status = :status
              and v.versionNumber = (select max(latest.versionNumber) from DocumentVersion latest
                                     where latest.document = v.document)
              and (:departmentId is null or v.document.department.id = :departmentId)
              and v.document.requiredPosition in :positions
              and (:excludedAuthorId is null or v.document.author.id <> :excludedAuthorId)
            """)
    Page<DocumentVersion> findReviewQueue(@Param("status") DocumentStatus status,
                                          @Param("departmentId") Long departmentId,
                                          @Param("positions") List<Position> positions,
                                          @Param("excludedAuthorId") Long excludedAuthorId,
                                          Pageable pageable);
}
