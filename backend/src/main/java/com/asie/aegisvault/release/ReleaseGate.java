package com.asie.aegisvault.release;

import com.asie.aegisvault.Document.*;
import com.asie.aegisvault.attachment.FileStore;
import com.asie.aegisvault.project.*;
import com.asie.aegisvault.workflow.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReleaseGate {
  private final ProjectEvidence evidence;
  private final ProjectMemberRepository members;
  private final ProjectRequirementRepository requirements;
  private final EngineeringChangeRepository changes;
  private final QualityRunRepository runs;
  private final NonconformityRepository defects;
  private final SecurityAssessmentRepository security;
  private final QualityCheckRepository checks;
  private final FileStore store;

  public Decision inspect(Project project, boolean verifyFiles) {
    Long id = project.getId();
    ProjectEvidence.Snapshot snapshot = evidence.snapshot(id);
    List<String> blockers = new ArrayList<>();
    if (project.getStatus() != ProjectStatus.REVIEW) blockers.add("프로젝트를 배포 검토 상태로 전환해주세요.");
    if (project.getDepartment().isClosed()) blockers.add("담당 부서가 폐쇄되었습니다.");
    if (members.activeOwners(id) == 0) blockers.add("활성 프로젝트 책임자가 필요합니다.");
    if (snapshot.versions().isEmpty()) blockers.add("배포할 문서가 없습니다.");
    long pending =
        snapshot.versions().stream().filter(v -> v.status() != DocumentStatus.APPROVED).count();
    if (pending > 0) blockers.add("최신 버전이 승인되지 않은 문서: " + pending + "건");
    for (var r : requirements.findByProjectIdOrderByCategory(id)) {
      long count =
          snapshot.versions().stream()
              .filter(v -> v.category() == r.getCategory() && v.status() == DocumentStatus.APPROVED)
              .count();
      if (count < r.getMinimumCount())
        blockers.add(
            r.getCategory().getLabel() + " 승인 문서 부족: " + count + " / " + r.getMinimumCount());
    }
    long openChanges =
        changes.countByProjectIdAndStatusIn(
            id, List.of(EngineeringChange.Status.OPEN, EngineeringChange.Status.IN_PROGRESS));
    if (openChanges > 0) blockers.add("미해결 변경 요청: " + openChanges + "건");
    long openDefects = defects.countByProjectIdAndClosedFalse(id);
    if (openDefects > 0) blockers.add("미해결 부적합: " + openDefects + "건");
    if (checks.findByProjectIdAndActiveTrueOrderById(id).isEmpty())
      blockers.add("품질시험 체크리스트가 없습니다.");
    QualityRun quality = runs.findFirstByProjectIdOrderByIdDesc(id).orElse(null);
    if (quality == null
        || !quality.isPassed()
        || !quality.getFingerprint().equals(snapshot.fingerprint()))
      blockers.add("현재 문서 구성에 대한 최신 품질 적합 결과가 필요합니다.");
    SecurityAssessment assessment = security.findFirstByProjectIdOrderByIdDesc(id).orElse(null);
    if (assessment == null
        || !assessment.isApproved()
        || !assessment.getFingerprint().equals(snapshot.fingerprint()))
      blockers.add("현재 문서 구성에 대한 최신 보안 승인이 필요합니다.");
    if (quality != null
        && assessment != null
        && Objects.equals(quality.getTesterId(), assessment.getReviewerId()))
      blockers.add("품질시험과 보안 검토의 독립성이 충족되지 않았습니다.");
    if (verifyFiles && snapshot.attachments().stream().anyMatch(a -> !store.intact(a)))
      blockers.add("첨부파일 누락 또는 무결성 오류가 있습니다.");
    return new Decision(
        List.copyOf(blockers),
        snapshot,
        quality == null ? null : quality.getId(),
        assessment == null ? null : assessment.getId());
  }

  public record Decision(
      List<String> blockers,
      ProjectEvidence.Snapshot snapshot,
      Long qualityRunId,
      Long securityAssessmentId) {
    public boolean ready() {
      return blockers.isEmpty();
    }
  }
}
