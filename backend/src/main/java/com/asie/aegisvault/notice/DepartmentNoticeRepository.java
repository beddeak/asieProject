package com.asie.aegisvault.notice;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentNoticeRepository extends JpaRepository<DepartmentNotice, Long> {
  @Query(
      """
      select new com.asie.aegisvault.notice.NoticeSummary(
          n.id, n.title, n.author.nickname, n.createdAt, n.updatedAt)
      from DepartmentNotice n where n.department.id = :departmentId
      """)
  Page<NoticeSummary> findSummaries(@Param("departmentId") Long departmentId, Pageable pageable);

  @Query(
      "select new"
          + " com.asie.aegisvault.notice.NoticeSummary(n.id,n.title,n.author.nickname,n.createdAt,n.updatedAt)"
          + " from DepartmentNotice n where n.scope=:scope and (:keyword is null or lower(n.title)"
          + " like :keyword escape '\\')")
  Page<NoticeSummary> global(
      @Param("scope") DepartmentNotice.Scope scope,
      @Param("keyword") String keyword,
      Pageable page);

  @EntityGraph(attributePaths = {"author", "department"})
  Optional<DepartmentNotice> findByIdAndScope(Long id, DepartmentNotice.Scope scope);

  @EntityGraph(attributePaths = {"department", "author"})
  Optional<DepartmentNotice> findByIdAndDepartmentId(Long id, Long departmentId);
}
