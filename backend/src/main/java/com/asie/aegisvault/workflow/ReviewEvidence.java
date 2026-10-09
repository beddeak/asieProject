package com.asie.aegisvault.workflow;

import com.asie.aegisvault.Document.dto.VersionManifest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewEvidence {
  private final ReviewDocumentRepository documents;

  /** Called in the same project-locked transaction as the review result. */
  public void captureQuality(Long runId, ProjectEvidence.Snapshot snapshot) {
    documents.saveAll(
        snapshot.versions().stream()
            .map(v -> ReviewDocument.quality(runId, v.versionId()))
            .toList());
  }

  public void captureSecurity(Long assessmentId, ProjectEvidence.Snapshot snapshot) {
    documents.saveAll(
        snapshot.versions().stream()
            .map(v -> ReviewDocument.security(assessmentId, v.versionId()))
            .toList());
  }

  public Review quality(QualityRun run, ProjectEvidence.Snapshot snapshot) {
    if (run == null) return missing("품질시험");
    return compare(
        run.getId(),
        run.getFingerprint(),
        run.isPassed(),
        documents.qualityBaseline(run.getId()),
        snapshot,
        "품질시험",
        "적합",
        "부적합");
  }

  public Review security(SecurityAssessment assessment, ProjectEvidence.Snapshot snapshot) {
    if (assessment == null) return missing("보안 검토");
    return compare(
        assessment.getId(),
        assessment.getFingerprint(),
        assessment.isApproved(),
        documents.securityBaseline(assessment.getId()),
        snapshot,
        "보안 검토",
        "승인",
        "반려");
  }

  private Review missing(String name) {
    return new Review(
        null, State.MISSING, "현재 문서 구성에 대한 " + name + " 결과가 필요합니다.", false, List.of());
  }

  private Review compare(
      Long id,
      String fingerprint,
      boolean passed,
      List<VersionManifest> baseline,
      ProjectEvidence.Snapshot snapshot,
      String name,
      String success,
      String failure) {
    boolean available = !baseline.isEmpty();
    if (Objects.equals(fingerprint, snapshot.fingerprint())) {
      return new Review(
          id,
          passed ? State.CURRENT : State.FAILED,
          passed
              ? "현재 문서 구성에 대한 " + name + " " + success + " 결과입니다."
              : "현재 문서 구성의 " + name + " 결과가 " + failure + "입니다. 조치 후 다시 검토해주세요.",
          available,
          List.of());
    }
    // Older results have only the fingerprint. Do not reconstruct their historical documents.
    List<DocumentChange> changed = available ? changes(baseline, snapshot.versions()) : List.of();
    String reason =
        !available
            ? "검토 이후 문서 구성 또는 검증 조건이 변경되었습니다. 당시 문서 버전이 저장되지 않은 기록이므로 변경 문서를 특정할 수 없습니다."
            : changed.isEmpty()
                ? "문서 버전은 같지만 보안등급·첨부파일·필수 문서 조건·시험 기준 등 검증 조건이 변경되었습니다."
                : "검토 당시와 현재의 문서 구성이 " + changed.size() + "건 다릅니다. 이전 결과로 배포할 수 없습니다.";
    return new Review(
        id, State.STALE, reason + " 현재 구성에 대한 " + name + " 결과를 새로 등록해주세요.", available, changed);
  }

  private List<DocumentChange> changes(
      List<VersionManifest> baseline, List<VersionManifest> current) {
    Map<Long, VersionManifest> before = new TreeMap<>();
    Map<Long, VersionManifest> after = new TreeMap<>();
    baseline.forEach(v -> before.put(v.documentId(), v));
    current.forEach(v -> after.put(v.documentId(), v));
    Set<Long> ids = new TreeSet<>(before.keySet());
    ids.addAll(after.keySet());
    List<DocumentChange> changed = new ArrayList<>();
    for (Long id : ids) {
      VersionManifest previous = before.get(id);
      VersionManifest latest = after.get(id);
      if (previous != null
          && latest != null
          && Objects.equals(previous.versionId(), latest.versionId())) continue;
      changed.add(
          new DocumentChange(
              id,
              latest == null ? previous.title() : latest.title(),
              previous == null ? null : previous.versionId(),
              previous == null ? null : previous.number(),
              latest == null ? null : latest.versionId(),
              latest == null ? null : latest.number(),
              previous != null,
              latest != null));
    }
    return List.copyOf(changed);
  }

  public enum State {
    MISSING,
    CURRENT,
    STALE,
    FAILED
  }

  public record Review(
      Long id,
      State state,
      String message,
      boolean baselineAvailable,
      List<DocumentChange> changes) {
    public Review {
      changes = List.copyOf(changes);
    }

    public Review forReader(Map<Long, String> readableTitles) {
      return new Review(
          id,
          state,
          message,
          baselineAvailable,
          changes.stream().map(change -> change.forReader(readableTitles)).toList());
    }

    public String label() {
      return switch (state) {
        case MISSING -> "검토 필요";
        case CURRENT -> "현재 구성 검증 완료";
        case STALE -> "변경 후 재검토 필요";
        case FAILED -> "조치 필요";
      };
    }
  }

  public record DocumentChange(
      Long documentId,
      String title,
      Long previousVersionId,
      Integer previousNumber,
      Long currentVersionId,
      Integer currentNumber,
      boolean previousReadable,
      boolean currentReadable) {
    public DocumentChange forReader(Map<Long, String> readableTitles) {
      boolean before = previousVersionId != null && readableTitles.containsKey(previousVersionId);
      boolean now = currentVersionId != null && readableTitles.containsKey(currentVersionId);
      String visibleTitle =
          now
              ? readableTitles.get(currentVersionId)
              : before ? readableTitles.get(previousVersionId) : "열람 제한 문서";
      return new DocumentChange(
          documentId,
          visibleTitle,
          previousVersionId,
          previousNumber,
          currentVersionId,
          currentNumber,
          before,
          now);
    }
  }
}
