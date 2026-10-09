package com.asie.aegisvault.workflow;

import com.asie.aegisvault.Document.dto.VersionManifest;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReviewDocumentRepository extends JpaRepository<ReviewDocument, Long> {
  @Query(
      """
      select new com.asie.aegisvault.Document.dto.VersionManifest(d.id,v.id,v.versionNumber,v.status,d.category,d.classification,d.author.id,v.editorId,v.title)
      from ReviewDocument r join DocumentVersion v on v.id=r.versionId join v.document d
      where r.qualityRunId=:runId order by d.id
      """)
  List<VersionManifest> qualityBaseline(@Param("runId") Long runId);

  @Query(
      """
      select new com.asie.aegisvault.Document.dto.VersionManifest(d.id,v.id,v.versionNumber,v.status,d.category,d.classification,d.author.id,v.editorId,v.title)
      from ReviewDocument r join DocumentVersion v on v.id=r.versionId join v.document d
      where r.securityAssessmentId=:assessmentId order by d.id
      """)
  List<VersionManifest> securityBaseline(@Param("assessmentId") Long assessmentId);
}
