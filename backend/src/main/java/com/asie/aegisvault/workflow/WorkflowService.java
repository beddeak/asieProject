package com.asie.aegisvault.workflow;

import com.asie.aegisvault.Document.*;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.UserRepository;
import com.asie.aegisvault.audit.AuditEvent;
import com.asie.aegisvault.common.PageQueries;
import com.asie.aegisvault.project.*;
import com.asie.aegisvault.security.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkflowService {
  private final CurrentUser actors;
  private final ProjectAccess access;
  private final ProjectMemberRepository members;
  private final UserRepository users;
  private final DocumentVersionRepository versions;
  private final DocumentRepository documents;
  private final DocumentAccess documentAccess;
  private final EngineeringChangeRepository changes;
  private final QualityCheckRepository checks;
  private final QualityRunRepository runs;
  private final QualityResultRepository results;
  private final NonconformityRepository defects;
  private final SecurityAssessmentRepository assessments;
  private final ProjectEvidence evidence;
  private final ApplicationEventPublisher events;

  public Page<EngineeringChange> changes(String actor, Long projectId, int page) {
    reader(actor, projectId);
    return PageQueries.fetch(
        page, 20, Sort.by(Sort.Direction.DESC, "id"), p -> changes.findByProjectId(projectId, p));
  }

  public List<QualityCheck> checklist(String actor, Long projectId) {
    reader(actor, projectId);
    return checks.findByProjectIdAndActiveTrueOrderById(projectId);
  }

  public Page<QualityRun> runs(String actor, Long projectId, int page) {
    reader(actor, projectId);
    return PageQueries.fetch(
        page, 20, Sort.by(Sort.Direction.DESC, "id"), p -> runs.findByProjectId(projectId, p));
  }

  public List<QualityResult> results(String actor, Long projectId, Long runId) {
    QualityRun run =
        runs.findById(runId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!Objects.equals(run.getProjectId(), projectId))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    reader(actor, projectId);
    return results.findByRunIdOrderById(runId);
  }

  public Page<Nonconformity> defects(String actor, Long projectId, int page) {
    reader(actor, projectId);
    return PageQueries.fetch(
        page, 20, Sort.by(Sort.Direction.DESC, "id"), p -> defects.findByProjectId(projectId, p));
  }

  public Page<SecurityAssessment> assessments(String actor, Long projectId, int page) {
    reader(actor, projectId);
    return PageQueries.fetch(
        page,
        20,
        Sort.by(Sort.Direction.DESC, "id"),
        p -> assessments.findByProjectId(projectId, p));
  }

  @Transactional
  public Long requestChange(
      String actor, Long projectId, String title, String description, Long baseVersionId) {
    User user = actors.get(actor);
    Project project =
        access.lock(
            user,
            projectId,
            ProjectRole.OWNER,
            ProjectRole.ENGINEERING,
            ProjectRole.CONTRIBUTOR,
            ProjectRole.QUALITY,
            ProjectRole.SECURITY);
    DocumentVersion base = projectVersion(user, projectId, baseVersionId);
    if (base.getStatus() != DocumentStatus.APPROVED)
      throw new IllegalArgumentException("승인된 기준 버전을 선택해주세요.");
    EngineeringChange change =
        changes.save(new EngineeringChange(projectId, title, description, baseVersionId, user));
    project.markChanged();
    event(
        user,
        projectId,
        "CHANGE_REQUESTED",
        "CHANGE",
        change.getId(),
        "연구개발 변경 요청 #" + change.getId(),
        ProjectRole.ENGINEERING,
        ProjectRole.OWNER);
    return change.getId();
  }

  @Transactional
  public void assignChange(String actor, Long projectId, Long id, Long assigneeId) {
    User user = actors.get(actor);
    access.lock(user, projectId, ProjectRole.OWNER, ProjectRole.ENGINEERING);
    EngineeringChange change = change(id);
    sameProject(projectId, change.getProjectId());
    User assignee =
        users
            .findById(assigneeId)
            .orElseThrow(() -> new IllegalArgumentException("담당자를 찾을 수 없습니다."));
    if (assignee.getAccountStatus() != com.asie.aegisvault.User.AccountStatus.ACTIVE
        || !access.hasRole(
            assignee, change.getProjectId(), ProjectRole.ENGINEERING, ProjectRole.OWNER))
      throw new IllegalArgumentException("활성 연구개발 담당자를 선택해주세요.");
    change.assign(assignee);
    event(
        user,
        change.getProjectId(),
        "CHANGE_ASSIGNED",
        "CHANGE",
        id,
        "변경 담당자: " + assignee.getNickname(),
        ProjectRole.ENGINEERING);
  }

  @Transactional
  public void resolveChange(
      String actor, Long projectId, Long id, Long nextVersionId, String reason, boolean reject) {
    User user = actors.get(actor);
    Project project = access.lock(user, projectId, ProjectRole.OWNER, ProjectRole.ENGINEERING);
    EngineeringChange change = change(id);
    sameProject(projectId, change.getProjectId());
    if (!reject) {
      DocumentVersion base = projectVersion(user, project.getId(), change.getBaseVersionId());
      DocumentVersion next = projectVersion(user, project.getId(), nextVersionId);
      if (!Objects.equals(base.getDocument().getId(), next.getDocument().getId())
          || next.getVersionNumber() <= base.getVersionNumber()
          || next.getStatus() != DocumentStatus.APPROVED)
        throw new IllegalArgumentException("기준 문서의 승인된 후속 버전을 연결해주세요.");
    }
    change.resolve(nextVersionId, reason, reject);
    project.markChanged();
    event(
        user,
        project.getId(),
        "CHANGE_RESOLVED",
        "CHANGE",
        id,
        "변경 요청 처리: " + change.getStatus(),
        ProjectRole.OWNER);
  }

  @Transactional
  public void addCheck(String actor, Long projectId, String criterion) {
    User user = actors.get(actor);
    Project project = access.lock(user, projectId, ProjectRole.QUALITY);
    checks.save(new QualityCheck(projectId, criterion));
    project.markChanged();
    event(
        user,
        projectId,
        "QUALITY_CHECK_ADDED",
        "PROJECT",
        projectId,
        "품질시험 기준 추가",
        ProjectRole.QUALITY);
  }

  @Transactional
  public void retireCheck(String actor, Long projectId, Long id) {
    User user = actors.get(actor);
    Project project = access.lock(user, projectId, ProjectRole.QUALITY);
    QualityCheck check =
        checks.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    sameProject(projectId, check.getProjectId());
    check.retire();
    project.markChanged();
    event(
        user,
        project.getId(),
        "QUALITY_CHECK_RETIRED",
        "QUALITY_CHECK",
        id,
        "품질시험 기준 종료",
        ProjectRole.QUALITY);
  }

  @Transactional
  public Long test(
      String actor,
      Long projectId,
      String expectedFingerprint,
      Set<Long> passedChecks,
      String findings,
      Long retestOf) {
    User user = actors.get(actor);
    Project project = access.lock(user, projectId, ProjectRole.QUALITY);
    ProjectEvidence.Snapshot snapshot = ready(user, projectId, expectedFingerprint);
    List<QualityCheck> checklist = checks.findByProjectIdAndActiveTrueOrderById(projectId);
    if (checklist.isEmpty()) throw new IllegalArgumentException("시험 기준을 먼저 등록해주세요.");
    Set<Long> actual = new HashSet<>(checklist.stream().map(QualityCheck::getId).toList());
    if (!actual.containsAll(passedChecks))
      throw new IllegalArgumentException("다른 프로젝트 또는 종료된 시험 항목이 포함되어 있습니다.");
    if (retestOf != null) {
      QualityRun previous =
          runs.findById(retestOf)
              .orElseThrow(() -> new IllegalArgumentException("재시험 대상을 찾을 수 없습니다."));
      if (!Objects.equals(previous.getProjectId(), projectId) || previous.isPassed())
        throw new IllegalArgumentException("이 프로젝트의 부적합 시험을 선택해주세요.");
    }
    boolean passed = actual.equals(passedChecks);
    QualityRun run =
        runs.save(
            new QualityRun(projectId, snapshot.fingerprint(), user, passed, findings, retestOf));
    results.saveAll(
        checklist.stream()
            .map(c -> new QualityResult(run.getId(), c, passedChecks.contains(c.getId())))
            .toList());
    if (!passed) defects.save(new Nonconformity(projectId, run.getId(), findings));
    project.markChanged();
    event(
        user,
        projectId,
        "QUALITY_TESTED",
        "QUALITY_RUN",
        run.getId(),
        "품질시험 #" + run.getId() + ": " + (passed ? "적합" : "부적합"),
        ProjectRole.OWNER,
        ProjectRole.ENGINEERING,
        ProjectRole.QUALITY);
    return run.getId();
  }

  @Transactional
  public void closeDefect(
      String actor, Long projectId, Long id, Long passingRunId, String correctiveAction) {
    User user = actors.get(actor);
    Project project = access.lock(user, projectId, ProjectRole.QUALITY);
    Nonconformity defect =
        defects.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    sameProject(projectId, defect.getProjectId());
    QualityRun run =
        runs.findById(passingRunId)
            .orElseThrow(() -> new IllegalArgumentException("재시험 결과를 찾을 수 없습니다."));
    if (!Objects.equals(run.getProjectId(), project.getId())
        || !run.isPassed()
        || !Objects.equals(run.getRetestOf(), defect.getFailedRunId())
        || !run.getFingerprint().equals(evidence.snapshot(project.getId()).fingerprint()))
      throw new IllegalArgumentException("현재 문서 구성을 대상으로 한 해당 시험의 적합 재시험 결과가 필요합니다.");
    defect.close(passingRunId, correctiveAction, user.getNickname());
    project.markChanged();
    event(
        user,
        project.getId(),
        "NONCONFORMITY_CLOSED",
        "NONCONFORMITY",
        id,
        "부적합 #" + id + " 시정 조치 완료",
        ProjectRole.OWNER,
        ProjectRole.QUALITY);
  }

  @Transactional
  public void assess(
      String actor, Long projectId, String expectedFingerprint, boolean approved, String findings) {
    User user = actors.get(actor);
    Project project = access.lock(user, projectId, ProjectRole.SECURITY);
    ProjectEvidence.Snapshot snapshot = ready(user, projectId, expectedFingerprint);
    QualityRun run = runs.findFirstByProjectIdOrderByIdDesc(projectId).orElse(null);
    if (run != null && Objects.equals(run.getTesterId(), user.getId()))
      throw new AccessDeniedException("품질시험 수행자와 보안 검토자는 달라야 합니다.");
    SecurityAssessment assessment =
        assessments.save(
            new SecurityAssessment(projectId, snapshot.fingerprint(), user, approved, findings));
    project.markChanged();
    event(
        user,
        projectId,
        "SECURITY_ASSESSED",
        "SECURITY_ASSESSMENT",
        assessment.getId(),
        "보안 검토: " + (approved ? "승인" : "반려"),
        ProjectRole.OWNER,
        ProjectRole.SECURITY);
  }

  public String fingerprint(String actor, Long projectId) {
    reader(actor, projectId);
    return evidence.snapshot(projectId).fingerprint();
  }

  private ProjectEvidence.Snapshot ready(User user, Long projectId, String expected) {
    ProjectEvidence.Snapshot snapshot = evidence.snapshot(projectId);
    if (!Objects.equals(expected, snapshot.fingerprint()))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "검토 대상이 변경되었습니다. 최신 문서로 다시 검토해주세요.");
    if (snapshot.versions().isEmpty()
        || snapshot.versions().stream().anyMatch(v -> v.status() != DocumentStatus.APPROVED))
      throw new IllegalArgumentException("대상 문서가 모두 승인된 후 검토해주세요.");
    for (var entry : snapshot.versions()) {
      if (Objects.equals(user.getId(), entry.authorId())
          || Objects.equals(user.getId(), entry.editorId()))
        throw new AccessDeniedException("문서 작성자는 해당 배포 구성의 독립 검토를 수행할 수 없습니다.");
    }
    documentAccess.requireScopes(
        user, documents.forVersions(snapshot.versions().stream().map(v -> v.versionId()).toList()));
    return snapshot;
  }

  private DocumentVersion projectVersion(User user, Long projectId, Long versionId) {
    if (versionId == null) throw new IllegalArgumentException("문서 버전을 선택해주세요.");
    DocumentVersion version =
        versions
            .findForDisplayById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("문서 버전을 찾을 수 없습니다."));
    if (version.getDocument().getProject() == null
        || !Objects.equals(version.getDocument().getProject().getId(), projectId))
      throw new IllegalArgumentException("이 프로젝트의 문서 버전을 선택해주세요.");
    documentAccess.requireRead(user, version);
    return version;
  }

  private void reader(String actor, Long projectId) {
    User user = actors.get(actor);
    access.read(user, projectId);
    documentAccess.requireScopes(user, documents.findByProjectId(projectId));
  }

  private void sameProject(Long expected, Long actual) {
    if (!Objects.equals(expected, actual))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트의 업무 항목을 찾을 수 없습니다.");
  }

  private EngineeringChange change(Long id) {
    return changes
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private void event(
      User actor,
      Long projectId,
      String action,
      String type,
      Long id,
      String description,
      ProjectRole... roles) {
    events.publishEvent(
        AuditEvent.of(actor, action, type, id, projectId, description)
            .notify(
                members.recipients(projectId, List.of(roles)), "/projects/" + projectId + "/work"));
  }
}
