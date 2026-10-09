package com.asie.aegisvault.project;

import com.asie.aegisvault.Document.DocumentRepository;
import com.asie.aegisvault.release.*;
import com.asie.aegisvault.security.CurrentUser;
import com.asie.aegisvault.security.DocumentAccess;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectProgressService {
  private final CurrentUser actors;
  private final ProjectAccess access;
  private final DocumentRepository documents;
  private final DocumentAccess documentAccess;
  private final ReleaseGate gate;
  private final ReleaseRepository releases;

  /** Basic project pages remain readable even when a member cannot inspect classified evidence. */
  public ProjectProgress view(String actor, Long projectId) {
    var user = actors.get(actor);
    Project project = access.read(user, projectId);
    try {
      documentAccess.requireScopes(user, documents.findByProjectId(projectId));
    } catch (AccessDeniedException denied) {
      return restricted(projectId);
    }
    return summarize(projectId, project.getStatus(), gate.inspect(project, false));
  }

  /** Workflow pages reuse their authorized gate result instead of running the checks twice. */
  public ProjectProgress from(ProjectDetail project, ReleaseGate.Decision decision) {
    return summarize(project.id(), project.status(), decision);
  }

  private ProjectProgress summarize(Long id, ProjectStatus status, ReleaseGate.Decision decision) {
    if (status == ProjectStatus.CLOSED) return closed(id);
    var groups = decision.issues().stream().collect(Collectors.groupingBy(GateIssue::stage));
    List<ProjectProgress.Step> steps = new ArrayList<>();
    boolean documentsReady = !groups.containsKey(ProjectProgress.Key.DOCUMENTS);
    for (ProjectProgress.Key key :
        List.of(
            ProjectProgress.Key.DOCUMENTS,
            ProjectProgress.Key.QUALITY,
            ProjectProgress.Key.SECURITY)) {
      var issues = groups.getOrDefault(key, List.of());
      ProjectProgress.State state =
          issues.isEmpty()
              ? ProjectProgress.State.COMPLETE
              : key != ProjectProgress.Key.DOCUMENTS && !documentsReady
                  ? ProjectProgress.State.BLOCKED
                  : ProjectProgress.State.NEEDS_ACTION;
      String summary =
          issues.isEmpty()
              ? completedSummary(key)
              : state == ProjectProgress.State.BLOCKED
                  ? "문서 준비를 먼저 마무리해주세요. " + issues.getFirst().message()
                  : issues.getFirst().message();
      steps.add(step(key, state, summary, actions(id, key, issues)));
    }

    boolean prerequisitesReady =
        steps.stream().allMatch(s -> s.state() == ProjectProgress.State.COMPLETE);
    var releaseIssues = groups.getOrDefault(ProjectProgress.Key.RELEASE, List.of());
    boolean administrativeBlock =
        releaseIssues.stream().anyMatch(i -> i.code() != GateIssue.Code.PROJECT_STATUS);
    Release published =
        status == ProjectStatus.RELEASED
            ? releases.findFirstByProjectIdOrderByReleaseNumberDesc(id).orElse(null)
            : null;
    boolean delivered =
        published != null
            && published.getRecalledAt() == null
            && Objects.equals(published.getFingerprint(), decision.snapshot().fingerprint())
            && prerequisitesReady
            && !administrativeBlock;
    ProjectProgress.State releaseState =
        delivered
            ? ProjectProgress.State.COMPLETE
            : !prerequisitesReady || administrativeBlock
                ? ProjectProgress.State.BLOCKED
                : ProjectProgress.State.NEEDS_ACTION;
    String releaseSummary =
        delivered
            ? "현재 구성의 배포 #" + published.getReleaseNumber() + "가 확정되었습니다."
            : !prerequisitesReady
                ? "문서·품질·보안의 남은 항목을 해결해야 배포할 수 있습니다."
                : !releaseIssues.isEmpty()
                    ? releaseIssues.getFirst().message()
                    : "검증 조건을 충족했습니다. 수신자를 확인하고 배포를 확정하세요.";
    var releaseActions =
        delivered
            ? List.of(new ProjectProgress.Action("확정된 배포 보기", "/releases/" + published.getId()))
            : actions(id, ProjectProgress.Key.RELEASE, releaseIssues);
    steps.add(step(ProjectProgress.Key.RELEASE, releaseState, releaseSummary, releaseActions));
    ProjectProgress.Step next =
        steps.stream()
            .filter(s -> s.state() != ProjectProgress.State.COMPLETE)
            .findFirst()
            .orElse(null);
    return new ProjectProgress(
        steps,
        next,
        delivered ? "현재 문서 구성의 검증과 배포를 완료했습니다." : "현재 문서 구성을 기준으로 단계별 준비 상태와 다음 행동을 확인하세요.",
        false);
  }

  private List<ProjectProgress.Action> actions(
      Long id, ProjectProgress.Key key, List<GateIssue> issues) {
    Map<String, ProjectProgress.Action> actions = new LinkedHashMap<>();
    for (GateIssue issue : issues)
      actions.putIfAbsent(
          issue.href(), new ProjectProgress.Action(issue.actionLabel(), issue.href()));
    ProjectProgress.Action destination =
        switch (key) {
          case DOCUMENTS ->
              new ProjectProgress.Action("프로젝트 문서 확인", "/projects/" + id + "#project-documents");
          case QUALITY ->
              new ProjectProgress.Action(
                  "품질시험 확인", "/projects/" + id + "/work?tab=quality#quality-review");
          case SECURITY ->
              new ProjectProgress.Action(
                  "보안 검토 확인", "/projects/" + id + "/work?tab=security#security-review");
          case RELEASE ->
              new ProjectProgress.Action("최종 배포 확인", "/projects/" + id + "/work?tab=release");
        };
    actions.putIfAbsent(destination.href(), destination);
    return List.copyOf(actions.values());
  }

  private ProjectProgress restricted(Long id) {
    var steps =
        Arrays.stream(ProjectProgress.Key.values())
            .map(
                key ->
                    step(
                        key,
                        ProjectProgress.State.BLOCKED,
                        "검증 근거를 열람할 권한이 없어 상태를 확인할 수 없습니다.",
                        List.of(
                            new ProjectProgress.Action(
                                "참여 역할 확인", "/projects/" + id + "/members"))))
            .toList();
    return new ProjectProgress(
        steps, null, "프로젝트 정보는 열람할 수 있습니다. 단계별 검증 상태는 문서 열람 권한을 충족해야 확인할 수 있습니다.", true);
  }

  private ProjectProgress closed(Long id) {
    var steps =
        Arrays.stream(ProjectProgress.Key.values())
            .map(
                key ->
                    step(
                        key,
                        ProjectProgress.State.BLOCKED,
                        "종료된 프로젝트입니다. 기존 기록을 확인할 수 있습니다.",
                        actions(id, key, List.of())))
            .toList();
    return new ProjectProgress(steps, null, "종료된 프로젝트는 새 작업과 배포를 진행할 수 없습니다.", false);
  }

  private ProjectProgress.Step step(
      ProjectProgress.Key key,
      ProjectProgress.State state,
      String summary,
      List<ProjectProgress.Action> actions) {
    String title =
        switch (key) {
          case DOCUMENTS -> "문서 준비";
          case QUALITY -> "품질시험";
          case SECURITY -> "보안 검토";
          case RELEASE -> "최종 배포";
        };
    String owner =
        switch (key) {
          case DOCUMENTS -> "작성·검토 담당자";
          case QUALITY -> "품질 담당자";
          case SECURITY -> "보안 담당자";
          case RELEASE -> "프로젝트 책임자";
        };
    return new ProjectProgress.Step(key, title, state, summary, owner, actions);
  }

  private String completedSummary(ProjectProgress.Key key) {
    return switch (key) {
      case DOCUMENTS -> "필수 승인 문서와 변경 요청 처리가 준비되었습니다.";
      case QUALITY -> "현재 구성의 품질시험이 적합하고 미해결 부적합이 없습니다.";
      case SECURITY -> "현재 구성의 독립 보안 검토를 완료했습니다.";
      case RELEASE -> throw new IllegalArgumentException("배포 완료는 확정된 배포 기록으로 확인합니다.");
    };
  }
}
