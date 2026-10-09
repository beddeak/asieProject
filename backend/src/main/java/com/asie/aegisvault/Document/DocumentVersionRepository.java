package com.asie.aegisvault.Document;

import com.asie.aegisvault.Document.dto.DocumentListItem;
import com.asie.aegisvault.Document.dto.ReviewQueueItem;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.security.SecurityClassification;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
  String SUMMARY =
      """
      select new com.asie.aegisvault.Document.dto.DocumentListItem(
          d.id, v.id, v.title, v.status, author.nickname, department.name,
          d.requiredPosition, v.versionNumber, v.createdAt, project.id, project.name,
          d.category,d.classification,d.archived)
      from DocumentVersion v
      join v.document d
      join d.author author
      join d.department department
      left join d.project project
      """;
  String VISIBILITY =
      """
        and (:admin=true or (
          d.requiredPosition in :positions and d.classification in :clearances
          and ((d.project is null and department.id=:departmentId)
            or exists(select m.id from ProjectMember m where m.project=d.project and m.user.id=:viewerId)
            or exists(select t.id from TemporaryAccess t where t.documentId=d.id and t.userId=:viewerId
              and t.status=com.asie.aegisvault.access.TemporaryAccess.Status.APPROVED and t.approvedAt<=:now and t.expiresAt>:now))
          and (author.id=:viewerId or v.editorId=:viewerId
            or v.status=com.asie.aegisvault.Document.DocumentStatus.APPROVED
            or (v.status=com.asie.aegisvault.Document.DocumentStatus.ARCHIVED and v.archivedFrom=com.asie.aegisvault.Document.DocumentStatus.APPROVED)
            or (:reviewer=true and d.project is null and department.id=:departmentId)
            or exists(select m.id from ProjectMember m where m.project=d.project and m.user.id=:viewerId
              and (m.role in (com.asie.aegisvault.project.ProjectRole.OWNER,com.asie.aegisvault.project.ProjectRole.ENGINEERING,com.asie.aegisvault.project.ProjectRole.CONTRIBUTOR)
                or (:securityReviewer=true and v.status<>com.asie.aegisvault.Document.DocumentStatus.DRAFT and m.role=com.asie.aegisvault.project.ProjectRole.SECURITY)))
          )))
      """;

  @Query(
      SUMMARY
          + """
          where v.versionNumber = (select max(latest.versionNumber) from DocumentVersion latest
                                   where latest.document = d)
            and (:includeArchived=true or d.archived=false)
            and (:mine=false or author.id=:viewerId or v.editorId=:viewerId)
            and (:reviewOnly=false or (v.status=com.asie.aegisvault.Document.DocumentStatus.PENDING_REVIEW
              and author.id<>:viewerId and (v.editorId is null or v.editorId<>:viewerId)
              and (:admin=true or (:reviewer=true and ((d.project is null and department.id=:departmentId)
                or exists(select m.id from ProjectMember m where m.project=d.project and m.user.id=:viewerId
                  and m.role in (com.asie.aegisvault.project.ProjectRole.OWNER,com.asie.aegisvault.project.ProjectRole.ENGINEERING)))))))
            and (:securityOnly=false or (v.status=com.asie.aegisvault.Document.DocumentStatus.PENDING_SECURITY_APPROVAL
              and author.id<>:viewerId and (v.editorId is null or v.editorId<>:viewerId)
              and (v.reviewedBy is null or v.reviewedBy.id<>:viewerId)
              and (:admin=true or (:securityReviewer=true and exists(select m.id from ProjectMember m where m.project=d.project and m.user.id=:viewerId and m.role=com.asie.aegisvault.project.ProjectRole.SECURITY)))))
            and (:keyword is null or lower(v.title) like :keyword escape '\\')
            and (:authorPattern is null or lower(author.nickname) like :authorPattern escape '\\')
            and (:status is null or v.status=:status)
            and (:category is null or d.category=:category)
            and (:projectId is null or project.id=:projectId)
            and (:filterDepartmentId is null or department.id=:filterDepartmentId)
          """
          + VISIBILITY)
  Page<DocumentListItem> findVisibleLatestVersions(
      @Param("admin") boolean admin,
      @Param("departmentId") Long departmentId,
      @Param("positions") List<Position> positions,
      @Param("reviewer") boolean reviewer,
      @Param("securityReviewer") boolean securityReviewer,
      @Param("viewerId") Long viewerId,
      @Param("clearances") List<SecurityClassification> clearances,
      @Param("now") Instant now,
      @Param("keyword") String keyword,
      @Param("authorPattern") String author,
      @Param("status") DocumentStatus status,
      @Param("category") DocumentCategory category,
      @Param("projectId") Long projectId,
      @Param("filterDepartmentId") Long filterDepartmentId,
      @Param("includeArchived") boolean includeArchived,
      @Param("mine") boolean mine,
      @Param("reviewOnly") boolean reviewOnly,
      @Param("securityOnly") boolean securityOnly,
      Pageable pageable);

  @Query(SUMMARY + "where d.id=:documentId " + VISIBILITY)
  Page<DocumentListItem> history(
      @Param("documentId") Long documentId,
      @Param("admin") boolean admin,
      @Param("departmentId") Long departmentId,
      @Param("positions") List<Position> positions,
      @Param("reviewer") boolean reviewer,
      @Param("securityReviewer") boolean securityReviewer,
      @Param("viewerId") Long viewerId,
      @Param("clearances") List<SecurityClassification> clearances,
      @Param("now") Instant now,
      Pageable pageable);

  @Query(SUMMARY + "where v.id in :versionIds " + VISIBILITY)
  List<DocumentListItem> findVisibleVersions(
      @Param("versionIds") Collection<Long> versionIds,
      @Param("admin") boolean admin,
      @Param("departmentId") Long departmentId,
      @Param("positions") List<Position> positions,
      @Param("reviewer") boolean reviewer,
      @Param("securityReviewer") boolean securityReviewer,
      @Param("viewerId") Long viewerId,
      @Param("clearances") List<SecurityClassification> clearances,
      @Param("now") Instant now);

  @EntityGraph(
      attributePaths = {"document.author", "document.department", "document.project", "reviewedBy"})
  Optional<DocumentVersion> findFirstByDocumentOrderByVersionNumberDesc(Document document);

  @EntityGraph(
      attributePaths = {"document.author", "document.department", "document.project", "reviewedBy"})
  Optional<DocumentVersion> findForDisplayById(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from DocumentVersion v where v.id = :id")
  Optional<DocumentVersion> findForReviewById(@Param("id") Long id);

  @Query(
      """
      select new com.asie.aegisvault.Document.dto.ReviewQueueItem(
          v.id, d.id, v.title, v.versionNumber, department.name, author.nickname,
          d.requiredPosition, v.createdAt)
      from DocumentVersion v
      join v.document d
      join d.department department
      join d.author author
      where v.status = :status
        and d.archived=false
        and v.versionNumber = (select max(latest.versionNumber) from DocumentVersion latest
                               where latest.document = v.document)
        and (:admin=true or (d.requiredPosition in :positions and d.classification in :clearances
          and ((d.project is null and department.id=:departmentId)
            or exists(select m.id from ProjectMember m where m.project=d.project and m.user.id=:excludedAuthorId
              and m.role in (com.asie.aegisvault.project.ProjectRole.OWNER,com.asie.aegisvault.project.ProjectRole.ENGINEERING)))))
        and (:excludedAuthorId is null or v.document.author.id <> :excludedAuthorId)
        and (v.editorId is null or v.editorId<>:excludedAuthorId)
      """)
  Page<ReviewQueueItem> findReviewQueue(
      @Param("status") DocumentStatus status,
      @Param("departmentId") Long departmentId,
      @Param("positions") List<Position> positions,
      @Param("excludedAuthorId") Long excludedAuthorId,
      @Param("admin") boolean admin,
      @Param("clearances") List<SecurityClassification> clearances,
      Pageable pageable);

  @Query(
      "select v.id from DocumentVersion v where v.document.id=:documentId and"
          + " v.versionNumber=(select max(x.versionNumber) from DocumentVersion x where"
          + " x.document.id=:documentId)")
  Optional<Long> latestId(@Param("documentId") Long documentId);

  @Query("select v.document.id from DocumentVersion v where v.id=:id")
  Optional<Long> documentId(@Param("id") Long id);

  @Query(
      """
      select new com.asie.aegisvault.Document.dto.VersionManifest(d.id,v.id,v.versionNumber,v.status,d.category,d.classification,d.author.id,v.editorId,v.title)
      from DocumentVersion v join v.document d where d.project.id=:projectId and d.archived=false
      and v.versionNumber=(select max(x.versionNumber) from DocumentVersion x where x.document=d)
      order by d.id
      """)
  List<com.asie.aegisvault.Document.dto.VersionManifest> currentProjectVersions(
      @Param("projectId") Long projectId);
}
