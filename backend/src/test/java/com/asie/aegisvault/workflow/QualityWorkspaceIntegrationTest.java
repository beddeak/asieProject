package com.asie.aegisvault.workflow;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.Department.DepartmentRepository;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.UserRepository;
import com.asie.aegisvault.project.ProjectForm;
import com.asie.aegisvault.project.ProjectRole;
import com.asie.aegisvault.project.ProjectService;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:workflow-suite;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
      "spring.jpa.hibernate.ddl-auto=validate",
      "logging.file.name=",
      "app.files.path=./build/test-files/workflow",
      "app.audit.key-file=./build/test-files/workflow-audit.key"
    })
@AutoConfigureMockMvc
class QualityWorkspaceIntegrationTest {
  @Autowired UserRepository users;
  @Autowired DepartmentRepository departments;
  @Autowired ProjectService projects;
  @Autowired QualityRunRepository runs;
  @Autowired QualityCheckRepository checks;
  @Autowired NonconformityRepository defects;
  @Autowired WorkflowService workflow;
  @Autowired ProjectEvidence evidence;
  @Autowired MockMvc mvc;
  private User owner, tester;
  private Long projectId;
  private String fingerprint;

  @BeforeEach
  void setup() {
    String prefix = "quality-ui-" + UUID.randomUUID().toString().substring(0, 8);
    Department department = departments.saveAndFlush(new Department(prefix, "품질 화면 검증"));
    owner = new User(prefix + "-owner", prefix + "-owner@example.test", "unused-password-hash");
    owner.assign(Position.MANAGER, department);
    owner = users.saveAndFlush(owner);
    tester = new User(prefix + "-tester", prefix + "-tester@example.test", "unused-password-hash");
    tester.assign(Position.STAFF, department);
    tester = users.saveAndFlush(tester);
    projectId = project(prefix);
    checks.saveAndFlush(new QualityCheck(projectId, "도면과 시험 기준 일치"));
    fingerprint = evidence.snapshot(projectId).fingerprint();
  }

  @Test
  void anOldFailureCanBeRetestedAndItsReportOpenedOutsideTheCurrentHistoryPage() throws Exception {
    QualityRun failed = run(projectId, fingerprint, false, null, "오래된 원시험 근거");
    Nonconformity original =
        defects.saveAndFlush(new Nonconformity(projectId, failed.getId(), "도면 치수 불일치"));
    for (int i = 0; i < 21; i++) {
      QualityRun recent = run(projectId, fingerprint, false, null, "후속 시험 " + i);
      defects.saveAndFlush(new Nonconformity(projectId, recent.getId(), "후속 부적합 " + i));
    }
    assertTrue(
        workflow.runs(tester.getNickname(), projectId, 0).stream()
            .noneMatch(r -> r.getId().equals(failed.getId())));
    assertTrue(
        workflow.defects(tester.getNickname(), projectId, 0).stream()
            .noneMatch(d -> d.getId().equals(original.getId())));
    mvc.perform(
            get(work())
                .param("tab", "quality")
                .param("retestOf", failed.getId().toString())
                .with(user(tester.getNickname())))
        .andExpect(status().isOk())
        .andExpect(
            result -> {
              String form = formBody(result.getResponse().getContentAsString(), work() + "/tests");
              assertTrue(
                  Pattern.compile(
                          "<input\\b(?=[^>]*\\bname=\"retestOf\")"
                              + "(?=[^>]*\\btype=\"hidden\")(?=[^>]*\\bvalue=\""
                              + failed.getId()
                              + "\")[^>]*>")
                      .matcher(form)
                      .find(),
                  "시험 제출 폼이 선택한 원시험 ID를 전송해야 합니다.");
            })
        .andExpect(content().string(containsString("원시험 결과 보기")));
    mvc.perform(
            get(work())
                .param("tab", "defects")
                .param("defectId", original.getId().toString())
                .with(user(tester.getNickname())))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("도면 치수 불일치")))
        .andExpect(content().string(not(containsString("후속 부적합 20"))));
    mvc.perform(get(work() + "/tests/" + failed.getId()).with(user(tester.getNickname())))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("오래된 원시험 근거")))
        .andExpect(content().string(containsString("defectId=" + original.getId())));
  }

  @Test
  void correctionChoicesOnlyExposeTheLatestPassingRetestForTheSameFailureAndCurrentScope()
      throws Exception {
    QualityRun failed = run(projectId, fingerprint, false, null, "원시험 근거");
    Nonconformity defect =
        defects.saveAndFlush(new Nonconformity(projectId, failed.getId(), "정합성 실패"));
    QualityRun first = run(projectId, fingerprint, true, failed.getId(), "첫 적합 재시험");
    QualityRun latest = run(projectId, fingerprint, true, failed.getId(), "최근 적합 재시험");
    run(projectId, "obsolete-fingerprint", true, failed.getId(), "이전 구성의 적합 시험");
    run(projectId, fingerprint, false, failed.getId(), "부적합 재시험");
    run(projectId, fingerprint, true, null, "연결 없는 적합 시험");
    Long otherProject = project("다른 프로젝트");
    run(otherProject, fingerprint, true, failed.getId(), "다른 프로젝트의 시험");
    var workspace = workflow.defectWorkspace(tester.getNickname(), projectId, 0, null);
    assertEquals(1, workspace.passingRetests().size());
    assertEquals(latest.getId(), workspace.passingRetests().get(failed.getId()).id());
    mvc.perform(get(work()).param("tab", "defects").with(user(tester.getNickname())))
        .andExpect(status().isOk())
        .andExpect(
            result -> {
              String form =
                  formBody(
                      result.getResponse().getContentAsString(),
                      work() + "/defects/" + defect.getId() + "/close");
              var selector =
                  Pattern.compile(
                          "<select\\b(?=[^>]*\\bname=\"passingRunId\")[^>]*>(.*?)</select>",
                          Pattern.DOTALL)
                      .matcher(form);
              assertTrue(selector.find(), "해당 부적합의 시정 완료 폼에 재시험 선택기가 있어야 합니다.");
              var choices =
                  Pattern.compile("<option\\b[^>]*\\bvalue=\"([^\"]*)\"")
                      .matcher(selector.group(1))
                      .results()
                      .map(match -> match.group(1))
                      .toList();
              assertEquals(
                  List.of("", latest.getId().toString()),
                  choices,
                  "안내 옵션과 현재 구성의 최신 적합 재시험만 선택할 수 있어야 합니다.");
              assertFalse(choices.contains(first.getId().toString()));
            });
    workflow.closeDefect(
        tester.getNickname(),
        projectId,
        defect.getId(),
        latest.getId(),
        "원인: 기준 오기. 조치: 기준과 도면 일치 후 재시험.");
    mvc.perform(get(work() + "/tests/" + latest.getId()).with(user(tester.getNickname())))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("시정 조치 완료")))
        .andExpect(content().string(containsString("원인: 기준 오기. 조치: 기준과 도면 일치 후 재시험.")))
        .andExpect(content().string(containsString("/tests/" + failed.getId())));
    checks.saveAndFlush(new QualityCheck(projectId, "추가 검증 기준"));
    mvc.perform(get(work() + "/tests/" + latest.getId()).with(user(tester.getNickname())))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("현재 배포의 적합 근거로 사용할 수 없습니다")));
  }

  @Test
  void focusedLinksCannotReadOrSelectAnotherProjectsQualityRecords() throws Exception {
    Long otherProject = project("다른 프로젝트");
    QualityRun otherRun = run(otherProject, fingerprint, false, null, "외부 시험 비공개 근거");
    Nonconformity otherDefect =
        defects.saveAndFlush(new Nonconformity(otherProject, otherRun.getId(), "외부 부적합 비공개"));
    mvc.perform(
            get(work())
                .param("tab", "quality")
                .param("retestOf", otherRun.getId().toString())
                .with(user(tester.getNickname())))
        .andExpect(status().isNotFound())
        .andExpect(content().string(not(containsString("외부 시험 비공개 근거"))));
    mvc.perform(
            get(work())
                .param("tab", "defects")
                .param("defectId", otherDefect.getId().toString())
                .with(user(tester.getNickname())))
        .andExpect(status().isNotFound());
    mvc.perform(get(work() + "/tests/" + otherRun.getId()).with(user(tester.getNickname())))
        .andExpect(status().isNotFound());
    QualityRun passed = run(projectId, fingerprint, true, null, "적합 시험");
    mvc.perform(
            get(work())
                .param("tab", "quality")
                .param("retestOf", passed.getId().toString())
                .with(user(tester.getNickname())))
        .andExpect(status().isBadRequest());
  }

  private String work() {
    return "/projects/" + projectId + "/work";
  }

  private static String formBody(String html, String action) {
    var form =
        Pattern.compile(
                "<form\\b(?=[^>]*\\baction=\"" + Pattern.quote(action) + "\")[^>]*>(.*?)</form>",
                Pattern.DOTALL)
            .matcher(html);
    assertTrue(form.find(), "예상한 제출 경로의 폼을 찾을 수 없습니다: " + action);
    return form.group(1);
  }

  private Long project(String name) {
    ProjectForm form = new ProjectForm();
    form.setName(name);
    form.setDescription("품질 업무 연결 검증");
    form.setDepartmentId(owner.getDepartment().getId());
    Long id = projects.create(owner.getNickname(), form);
    projects.assignMember(owner.getNickname(), id, tester.getId(), ProjectRole.QUALITY);
    projects.changeStatus(owner.getNickname(), id, "start");
    return id;
  }

  private QualityRun run(
      Long project, String scope, boolean passed, Long retestOf, String findings) {
    return runs.saveAndFlush(new QualityRun(project, scope, tester, passed, findings, retestOf));
  }
}
