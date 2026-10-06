package com.asie.aegisvault.notice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DepartmentNoticeRepository extends JpaRepository<DepartmentNotice, Long> {
    @Query("""
            select new com.asie.aegisvault.notice.NoticeSummary(
                n.id, n.title, n.author.nickname, n.createdAt, n.updatedAt)
            from DepartmentNotice n where n.department.id = :departmentId
            """)
    Page<NoticeSummary> findSummaries(@Param("departmentId") Long departmentId, Pageable pageable);

    @EntityGraph(attributePaths = {"department", "author"})
    Optional<DepartmentNotice> findByIdAndDepartmentId(Long id, Long departmentId);
}
