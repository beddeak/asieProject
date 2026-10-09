package com.asie.aegisvault.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.asie.aegisvault.Document.DocumentCategory;
import com.asie.aegisvault.Document.DocumentStatus;
import com.asie.aegisvault.Document.dto.VersionManifest;
import com.asie.aegisvault.security.SecurityClassification;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewEvidenceTest {
  private final ReviewDocumentRepository documents = mock(ReviewDocumentRepository.class);
  private final ReviewEvidence evidence = new ReviewEvidence(documents);

  @Test
  void explainsChangedAddedAndRemovedVersionsWithoutTreatingUnchangedDocumentsAsChanged() {
    var original = List.of(version(1, 10, 1), version(2, 20, 1), version(3, 30, 1));
    when(documents.qualityBaseline(7L)).thenReturn(original);
    var review =
        evidence.quality(
            quality(true),
            snapshot("new", version(1, 11, 2), version(3, 30, 1), version(4, 40, 1)));
    assertEquals(ReviewEvidence.State.STALE, review.state());
    assertTrue(review.baselineAvailable());
    assertEquals(
        List.of(1L, 2L, 4L),
        review.changes().stream().map(ReviewEvidence.DocumentChange::documentId).toList());
    var updated = review.changes().get(0);
    assertEquals(10L, updated.previousVersionId());
    assertEquals(11L, updated.currentVersionId());
    assertEquals(1, updated.previousNumber());
    assertEquals(2, updated.currentNumber());
    assertEquals("문서 1 v2", updated.title());
    var removed = review.changes().get(1);
    assertEquals("문서 2 v1", removed.title());
    assertNull(removed.currentVersionId());
    assertNull(removed.currentNumber());
    var added = review.changes().get(2);
    assertNull(added.previousVersionId());
    assertNull(added.previousNumber());
    assertEquals(40L, added.currentVersionId());
  }

  @Test
  void changedReviewConditionsRequireRevalidationEvenWhenDocumentVersionsMatch() {
    when(documents.qualityBaseline(7L)).thenReturn(List.of(version(1, 10, 1)));
    var review = evidence.quality(quality(true), snapshot("new-checklist", version(1, 10, 1)));
    assertEquals(ReviewEvidence.State.STALE, review.state());
    assertTrue(review.changes().isEmpty());
    assertTrue(review.message().contains("문서 버전은 같지만"));
    assertTrue(review.message().contains("검증 조건이 변경"));
  }

  @Test
  void historicalFingerprintStillDeterminesValidityWithoutInventingVersionChanges() {
    when(documents.qualityBaseline(7L)).thenReturn(List.of());
    var current = evidence.quality(quality(true), snapshot("old", version(1, 10, 1)));
    assertEquals(ReviewEvidence.State.CURRENT, current.state());
    assertFalse(current.baselineAvailable());
    var stale = evidence.quality(quality(true), snapshot("new", version(1, 11, 2)));
    assertEquals(ReviewEvidence.State.STALE, stale.state());
    assertFalse(stale.baselineAvailable());
    assertTrue(stale.changes().isEmpty());
    assertTrue(stale.message().contains("특정할 수 없습니다"));
  }

  @Test
  void failureAppliesOnlyToTheReviewedConfigurationAndMissingReviewsStayDistinct() {
    when(documents.qualityBaseline(7L)).thenReturn(List.of(version(1, 10, 1)));
    assertEquals(
        ReviewEvidence.State.FAILED,
        evidence.quality(quality(false), snapshot("old", version(1, 10, 1))).state());
    assertEquals(
        ReviewEvidence.State.STALE,
        evidence.quality(quality(false), snapshot("new", version(1, 11, 2))).state());
    assertEquals(
        ReviewEvidence.State.MISSING,
        evidence.quality(null, snapshot("old", version(1, 10, 1))).state());
    assertEquals(
        ReviewEvidence.State.MISSING,
        evidence.security(null, snapshot("old", version(1, 10, 1))).state());
  }

  @Test
  void qualityAndSecurityCompareAgainstTheirOwnReviewedVersions() {
    when(documents.qualityBaseline(7L)).thenReturn(List.of(version(1, 11, 2)));
    when(documents.securityBaseline(8L)).thenReturn(List.of(version(1, 10, 1)));
    var run = quality(true);
    when(run.getFingerprint()).thenReturn("new");
    var assessment = mock(SecurityAssessment.class);
    when(assessment.getId()).thenReturn(8L);
    when(assessment.getFingerprint()).thenReturn("old");
    when(assessment.isApproved()).thenReturn(true);
    var now = snapshot("new", version(1, 11, 2));
    assertEquals(ReviewEvidence.State.CURRENT, evidence.quality(run, now).state());
    var security = evidence.security(assessment, now);
    assertEquals(ReviewEvidence.State.STALE, security.state());
    assertEquals(10L, security.changes().getFirst().previousVersionId());
    assertEquals(11L, security.changes().getFirst().currentVersionId());
  }

  private QualityRun quality(boolean passed) {
    var run = mock(QualityRun.class);
    when(run.getId()).thenReturn(7L);
    when(run.getFingerprint()).thenReturn("old");
    when(run.isPassed()).thenReturn(passed);
    return run;
  }

  private VersionManifest version(long document, long id, int number) {
    return new VersionManifest(
        document,
        id,
        number,
        DocumentStatus.APPROVED,
        DocumentCategory.DESIGN,
        SecurityClassification.INTERNAL,
        1L,
        1L,
        "문서 " + document + " v" + number);
  }

  private ProjectEvidence.Snapshot snapshot(String fingerprint, VersionManifest... versions) {
    return new ProjectEvidence.Snapshot(List.of(versions), List.of(), fingerprint);
  }
}
