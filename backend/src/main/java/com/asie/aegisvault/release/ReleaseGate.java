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
  private final ReviewEvidence reviewEvidence;
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
    List<GateIssue> issues = new ArrayList<>();
    String overview = "/projects/" + id;
    String work = overview + "/work?tab=";
    if (project.getStatus() != ProjectStatus.REVIEW)
      issues.add(
          new GateIssue(
              ProjectProgress.Key.RELEASE,
              GateIssue.Code.PROJECT_STATUS,
              project.getStatus() == ProjectStatus.CLOSED
                  ? "종료된 프로젝트는 배포할 수 없습니다."
                  : "프로젝트를 배포 검토 상태로 전환해주세요.",
              "프로젝트 상태 확인",
              overview + "#project-status"));
    if (project.getDepartment().isClosed())
      issues.add(
          new GateIssue(
              ProjectProgress.Key.RELEASE,
              GateIssue.Code.CLOSED_DEPARTMENT,
              "담당 부서가 폐쇄되었습니다.",
              "담당 부서 확인",
              overview));
    if (members.activeOwners(id) == 0)
      issues.add(
          new GateIssue(
              ProjectProgress.Key.RELEASE,
              GateIssue.Code.OWNER_REQUIRED,
              "활성 프로젝트 책임자가 필요합니다.",
              "참여자 확인",
              overview + "/members"));
    if (snapshot.versions().isEmpty())
      issues.add(
          new GateIssue(
              ProjectProgress.Key.DOCUMENTS,
              GateIssue.Code.NO_DOCUMENTS,
              "배포할 문서가 없습니다.",
              "프로젝트 문서 준비",
              overview + "#project-documents"));
    long pending =
        snapshot.versions().stream().filter(v -> v.status() != DocumentStatus.APPROVED).count();
    if (pending > 0)
      issues.add(
          new GateIssue(
              ProjectProgress.Key.DOCUMENTS,
              GateIssue.Code.PENDING_DOCUMENTS,
              "최신 버전이 승인되지 않은 문서: " + pending + "건",
              "문서 승인 상태 확인",
              overview + "#project-documents"));
    for (var r : requirements.findByProjectIdOrderByCategory(id)) {
      long count =
          snapshot.versions().stream()
              .filter(v -> v.category() == r.getCategory() && v.status() == DocumentStatus.APPROVED)
              .count();
      if (count < r.getMinimumCount())
        issues.add(
            new GateIssue(
                ProjectProgress.Key.DOCUMENTS,
                GateIssue.Code.REQUIRED_DOCUMENTS,
                r.getCategory().getLabel() + " 승인 문서 부족: " + count + " / " + r.getMinimumCount(),
                "필수 문서 확인",
                overview + "#project-documents"));
    }
    long openChanges =
        changes.countByProjectIdAndStatusIn(
            id, List.of(EngineeringChange.Status.OPEN, EngineeringChange.Status.IN_PROGRESS));
    if (openChanges > 0)
      issues.add(
          new GateIssue(
              ProjectProgress.Key.DOCUMENTS,
              GateIssue.Code.OPEN_CHANGES,
              "미해결 변경 요청: " + openChanges + "건",
              "변경 요청 처리",
              work + "changes"));
    long openDefects = defects.countByProjectIdAndClosedFalse(id);
    if (openDefects > 0)
      issues.add(
          new GateIssue(
              ProjectProgress.Key.QUALITY,
              GateIssue.Code.OPEN_DEFECTS,
              "미해결 부적합: " + openDefects + "건",
              "부적합·시정 조치 확인",
              work + "defects"));
    if (checks.findByProjectIdAndActiveTrueOrderById(id).isEmpty())
      issues.add(
          new GateIssue(
              ProjectProgress.Key.QUALITY,
              GateIssue.Code.CHECKLIST_REQUIRED,
              "품질시험 체크리스트가 없습니다.",
              "시험 기준 준비",
              work + "quality#quality-review"));
    QualityRun quality = runs.findFirstByProjectIdOrderByIdDesc(id).orElse(null);
    ReviewEvidence.Review qualityReview = reviewEvidence.quality(quality, snapshot);
    if (qualityReview.state() != ReviewEvidence.State.CURRENT)
      issues.add(
          new GateIssue(
              ProjectProgress.Key.QUALITY,
              GateIssue.Code.QUALITY_REVIEW,
              "품질시험: " + qualityReview.message(),
              "품질시험 확인",
              work + "quality#quality-review"));
    SecurityAssessment assessment = security.findFirstByProjectIdOrderByIdDesc(id).orElse(null);
    ReviewEvidence.Review securityReview = reviewEvidence.security(assessment, snapshot);
    if (securityReview.state() != ReviewEvidence.State.CURRENT)
      issues.add(
          new GateIssue(
              ProjectProgress.Key.SECURITY,
              GateIssue.Code.SECURITY_REVIEW,
              "보안 검토: " + securityReview.message(),
              "보안 검토 확인",
              work + "security#security-review"));
    if (quality != null
        && assessment != null
        && Objects.equals(quality.getTesterId(), assessment.getReviewerId()))
      issues.add(
          new GateIssue(
              ProjectProgress.Key.SECURITY,
              GateIssue.Code.REVIEW_INDEPENDENCE,
              "품질시험과 보안 검토의 독립성이 충족되지 않았습니다.",
              "독립 보안 검토 확인",
              work + "security#security-review"));
    if (verifyFiles && snapshot.attachments().stream().anyMatch(a -> !store.intact(a)))
      issues.add(
          new GateIssue(
              ProjectProgress.Key.DOCUMENTS,
              GateIssue.Code.ATTACHMENT_INTEGRITY,
              "첨부파일 누락 또는 무결성 오류가 있습니다.",
              "문서 첨부 확인",
              overview + "#project-documents"));
    return new Decision(
        List.copyOf(issues),
        snapshot,
        quality == null ? null : quality.getId(),
        assessment == null ? null : assessment.getId(),
        qualityReview,
        securityReview);
  }

  public record Decision(
      List<GateIssue> issues,
      ProjectEvidence.Snapshot snapshot,
      Long qualityRunId,
      Long securityAssessmentId,
      ReviewEvidence.Review qualityReview,
      ReviewEvidence.Review securityReview) {
    public Decision {
      issues = List.copyOf(issues);
    }

    public List<String> blockers() {
      return issues.stream().map(GateIssue::message).toList();
    }

    public boolean ready() {
      return issues.isEmpty();
    }
  }
}
