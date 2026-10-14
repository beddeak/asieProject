package com.asie.aegisvault.Document;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from Document d where d.id=:id")
  Optional<Document> lockById(@Param("id") Long id);

  @Query("select d.project.id from Document d where d.id=:id")
  Optional<Long> projectId(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select d from Document d where d.department.id=:id order by d.id")
  List<Document> lockDepartmentDocuments(@Param("id") Long id);

  @Query("select distinct d from DocumentVersion v join v.document d where v.id in :versionIds")
  List<Document> forVersions(@Param("versionIds") List<Long> versionIds);

  List<Document> findByProjectId(Long projectId);
}
