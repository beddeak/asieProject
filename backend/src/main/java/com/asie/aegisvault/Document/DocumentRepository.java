package com.asie.aegisvault.Document;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from Document d where d.id=:id")
  Optional<Document> lockById(@Param("id") Long id);

  @Query("select d.project.id from Document d where d.id=:id")
  Optional<Long> projectId(@Param("id") Long id);

  @Query(
      "select d.id from Document d where d.project.id=:projectId and d.archived=false order by"
          + " d.id")
  List<Long> activeIds(@Param("projectId") Long projectId);

  long countByDepartmentIdAndArchivedFalse(Long departmentId);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select d from Document d where d.department.id=:id order by d.id")
  java.util.List<Document> lockDepartmentDocuments(
      @org.springframework.data.repository.query.Param("id") Long id);

  @Query("select distinct d from DocumentVersion v join v.document d where v.id in :versionIds")
  java.util.List<Document> forVersions(@Param("versionIds") java.util.List<Long> versionIds);
  java.util.List<Document> findByProjectId(Long projectId);
}
