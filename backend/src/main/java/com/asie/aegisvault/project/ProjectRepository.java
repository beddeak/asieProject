package com.asie.aegisvault.project;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {
  @EntityGraph(attributePaths = {"department", "createdBy"})
  Optional<Project> findForDisplayById(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Project p where p.id=:id")
  Optional<Project> lockById(@Param("id") Long id);

  String VISIBLE_SUMMARIES =
      """
      select new com.asie.aegisvault.project.ProjectSummary(p.id,p.name,d.name,p.status,p.targetDate,p.createdAt)
      from Project p join p.department d
      where (:admin=true or exists(select m.id from ProjectMember m where m.project=p and m.user.id=:userId))
      """;

  @Query(VISIBLE_SUMMARIES)
  java.util.List<ProjectSummary> recent(
      @Param("admin") boolean admin, @Param("userId") Long userId, Pageable pageable);

  @Query(
      VISIBLE_SUMMARIES
          + """
          and (:keyword is null or lower(p.name) like :keyword escape '\\')
          and (:status is null or p.status=:status)
          and (:departmentId is null or d.id=:departmentId)
          """)
  Page<ProjectSummary> search(
      @Param("admin") boolean admin,
      @Param("userId") Long userId,
      @Param("keyword") String keyword,
      @Param("status") ProjectStatus status,
      @Param("departmentId") Long departmentId,
      Pageable pageable);

  long countByDepartmentIdAndStatusNot(Long departmentId, ProjectStatus status);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select p from Project p where p.department.id=:id order by p.id")
  java.util.List<Project> lockDepartmentProjects(
      @org.springframework.data.repository.query.Param("id") Long id);
}
