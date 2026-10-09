package com.asie.aegisvault;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.asie.aegisvault.Department.*;
import com.asie.aegisvault.Document.*;
import com.asie.aegisvault.User.*;
import com.asie.aegisvault.attachment.*;
import com.asie.aegisvault.project.*;
import com.asie.aegisvault.release.*;
import com.asie.aegisvault.security.SecurityClassification;
import com.asie.aegisvault.workflow.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
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
class DocumentTraceIntegrationTest {
  @Autowired UserRepository users;
  @Autowired DepartmentRepository departments;
  @Autowired DocumentService documents;
  @Autowired DocumentVersionRepository versions;
  @Autowired ProjectService projects;
  @Autowired WorkflowService workflow;
  @Autowired QualityCheckRepository checks;
  @Autowired ReleaseService releases;
  @Autowired AttachmentService files;
  @Autowired AttachmentRepository attachments;
  @Autowired PasswordEncoder passwords;
  @Autowired MockMvc mvc;
  private User admin, owner, tester, security, viewer, outsider;
  private Long projectId;

  @BeforeEach
  void setup() {
    String prefix = "trace-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    var department = departments.saveAndFlush(new Department(prefix + "연구", "배포 추적"));
    admin = account(prefix + "admin", Position.ADMIN, department);
    owner = account(prefix + "owner", Position.MANAGER, department);
    tester = account(prefix + "quality", Position.MANAGER, department);
    security = account(prefix + "security", Position.MANAGER, department);
    viewer = account(prefix + "viewer", Position.STAFF, department);
    outsider = account(prefix + "outsider", Position.STAFF, department);
    var form = new ProjectForm();
    form.setName(prefix + "버전 추적");
    form.setDescription("문서 승인본과 배포 원본의 추적");
    form.setDepartmentId(department.getId());
    projectId = projects.create(owner.getNickname(), form);
    projects.assignMember(owner.getNickname(), projectId, tester.getId(), ProjectRole.QUALITY);
    projects.assignMember(owner.getNickname(), projectId, security.getId(), ProjectRole.SECURITY);
    projects.assignMember(owner.getNickname(), projectId, viewer.getId(), ProjectRole.VIEWER);
    projects.changeStatus(owner.getNickname(), projectId, "start");
  }

  private User account(String name, Position position, Department department) {
    var user = new User(name, name + "@example.test", passwords.encode("Test-password-42!"));
    user.assign(position, department);
    user.changeClearance(SecurityClassification.RESTRICTED);
    return users.saveAndFlush(user);
  }

  private DocumentVersion draft(DocumentCategory category, String title) {
    var document =
        documents.create(
            new DocumentCommand(
                title,
                "확정 대상 본문",
                Position.STAFF,
                projectId,
                category,
                SecurityClassification.INTERNAL,
                true),
            admin.getId());
    return versions.findFirstByDocumentOrderByVersionNumberDesc(document).orElseThrow();
  }

  private DocumentVersion approve(DocumentVersion draft) {
    documents.edit(
        draft.getId(),
        admin.getId(),
        draft.getRevision(),
        draft.getTitle(),
        draft.getContent(),
        true);
    return versions.findById(draft.getId()).orElseThrow();
  }

  @Test
  void approvedVersionRemainsVisibleWhileNewDraftDetailsStayPrivate() throws Exception {
    var first = approve(draft(DocumentCategory.DESIGN, "승인된 설계서"));
    Long documentId = first.getDocument().getId();
    Long nextId = documents.newVersion(documentId, admin.getId(), first.getId());
    var next = versions.findById(nextId).orElseThrow();
    documents.edit(nextId, admin.getId(), next.getRevision(), "비공개 차세대 설계", "초안 내용", false);

    var authorSummary = documents.historySummary(documentId, admin.getId());
    assertEquals(nextId, authorSummary.latestVisible().versionId());
    assertEquals(first.getId(), authorSummary.latestApproved().versionId());
    var readerSummary = documents.historySummary(documentId, viewer.getId());
    assertEquals(first.getId(), readerSummary.latestVisible().versionId());
    assertEquals(first.getId(), readerSummary.latestApproved().versionId());
    mvc.perform(get("/document/" + documentId + "/history").with(user(viewer.getNickname())))
        .andExpect(status().isOk())
        .andExpect(
            content().string(containsString("data-trace-approved=\"" + first.getId() + "\"")))
        .andExpect(content().string(containsString("최신 승인본")))
        .andExpect(content().string(not(containsString("비공개 차세대 설계"))))
        .andExpect(content().string(not(containsString("/document/versions/" + nextId + "\""))));
    mvc.perform(get("/document/versions/" + first.getId()).with(user(viewer.getNickname())))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("관리자 작성 문서 즉시 승인 정책")))
        .andExpect(content().string(not(containsString("비공개 차세대 설계"))));
    mvc.perform(get("/document/versions/" + nextId).with(user(viewer.getNickname())))
        .andExpect(status().isForbidden());
    mvc.perform(get("/document/" + documentId + "/history").with(user(outsider.getNickname())))
        .andExpect(status().isForbidden());

    next = versions.findById(nextId).orElseThrow();
    documents.edit(nextId, admin.getId(), next.getRevision(), "승인된 설계 v2", "검토 완료 본문", true);
    assertEquals(
        nextId, documents.historySummary(documentId, viewer.getId()).latestApproved().versionId());
    assertEquals(2, documents.history(documentId, viewer.getId(), 0).getTotalElements());
    assertEquals(
        DocumentStatus.APPROVED, versions.findById(first.getId()).orElseThrow().getStatus());
  }

  @Test
  void draftsHaveNoApprovalAndArchivedApprovalIsMarkedAsHistorical() throws Exception {
    var draft = draft(DocumentCategory.DESIGN, "초안 상태");
    assertNull(
        documents.historySummary(draft.getDocument().getId(), admin.getId()).latestApproved());
    mvc.perform(
            get("/document/" + draft.getDocument().getId() + "/history")
                .with(user(admin.getNickname())))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("아직 승인된 버전이 없습니다.")));
    var approved = approve(draft);
    documents.archive(approved.getDocument().getId(), admin.getId(), approved.getId());
    var summary = documents.historySummary(approved.getDocument().getId(), viewer.getId());
    assertTrue(summary.archived());
    assertEquals(approved.getId(), summary.latestApproved().versionId());
    mvc.perform(
            get("/document/" + approved.getDocument().getId() + "/history")
                .with(user(viewer.getNickname())))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("마지막 승인 이력")))
        .andExpect(
            result ->
                assertTrue(
                    result
                        .getResponse()
                        .getContentAsString()
                        .replaceAll("\\s+", " ")
                        .contains("현재 프로젝트 배포 구성에는 포함되지 않습니다."),
                    "보관 문서가 현재 배포 구성에서 제외된다는 안내가 표시되어야 합니다."));
  }

  @Test
  void approvedListFindsUsableApprovalThroughDraftsWithSearchPagingAndScopeChecks()
      throws Exception {
    var first = approve(draft(DocumentCategory.DESIGN, "추적 검색 설계A"));
    var second = approve(draft(DocumentCategory.DESIGN, "추적 검색 설계B"));
    Long nextId = documents.newVersion(first.getDocument().getId(), admin.getId(), first.getId());
    var next = versions.findById(nextId).orElseThrow();
    documents.edit(nextId, admin.getId(), next.getRevision(), "비공개 후속 초안 제목", "개정 중", false);
    documents.create(
        new DocumentCommand(
            "추적 검색 관리자 등급",
            "권한 제한 본문",
            Position.MANAGER,
            projectId,
            DocumentCategory.DESIGN,
            SecurityClassification.INTERNAL,
            false),
        admin.getId());

    var filter = new DocumentFilter();
    filter.setProjectId(projectId);
    assertEquals(
        List.of(second.getId()),
        documents.documentList(viewer.getId(), filter, 0).documents().stream()
            .map(v -> v.versionId())
            .toList());
    filter.setApprovedOnly(true);
    filter.setKeyword("추적 검색");
    filter.setSort("title");
    var firstPage = documents.documentList(viewer.getId(), filter, 0, 1).documents();
    assertEquals(2, firstPage.getTotalElements());
    assertEquals(first.getId(), firstPage.getContent().getFirst().versionId());
    assertEquals(
        second.getId(),
        documents
            .documentList(viewer.getId(), filter, 1, 1)
            .documents()
            .getContent()
            .getFirst()
            .versionId());
    assertEquals(
        0, documents.documentList(outsider.getId(), filter, 0).documents().getTotalElements());
    mvc.perform(
            get("/document/list")
                .with(user(viewer.getNickname()))
                .param("projectId", projectId.toString())
                .param("approvedOnly", "true")
                .param("q", "추적 검색")
                .param("sort", "title")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("문서별 최신 승인본")))
        .andExpect(
            content().string(containsString("href=\"/document/versions/" + first.getId() + "\"")))
        .andExpect(content().string(containsString("approvedOnly=true")))
        .andExpect(content().string(not(containsString("비공개 후속 초안 제목"))))
        .andExpect(content().string(not(containsString("추적 검색 관리자 등급"))));
    mvc.perform(
            get("/document/list")
                .with(user(viewer.getNickname()))
                .param("projectId", projectId.toString())
                .param("approvedOnly", "true")
                .param("q", "추적 검색")
                .param("sort", "title")
                .param("size", "1")
                .param("page", "1"))
        .andExpect(status().isOk())
        .andExpect(
            content().string(containsString("data-document-version=\"" + second.getId() + "\"")))
        .andExpect(
            content()
                .string(not(containsString("data-document-version=\"" + first.getId() + "\""))));

    filter.setKeyword("비공개 후속");
    assertTrue(documents.documentList(viewer.getId(), filter, 0).documents().isEmpty());
    next = versions.findById(nextId).orElseThrow();
    documents.edit(nextId, admin.getId(), next.getRevision(), "추적 검색 설계A v2", "개정 승인", true);
    filter.setKeyword("추적 검색");
    assertEquals(
        nextId,
        documents
            .documentList(viewer.getId(), filter, 0)
            .documents()
            .getContent()
            .getFirst()
            .versionId());
    documents.archive(first.getDocument().getId(), admin.getId(), nextId);
    assertEquals(
        1, documents.documentList(viewer.getId(), filter, 0).documents().getTotalElements());
    filter.setArchived(true);
    var archived =
        documents.documentList(viewer.getId(), filter, 0).documents().getContent().getFirst();
    assertEquals(nextId, archived.versionId());
    assertTrue(archived.archived());
    assertEquals(DocumentStatus.ARCHIVED, archived.status());
    mvc.perform(
            get("/document/list")
                .with(user(viewer.getNickname()))
                .param("projectId", projectId.toString())
                .param("approvedOnly", "true")
                .param("archived", "true")
                .param("q", "설계A"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("문서 보관됨")))
        .andExpect(content().string(containsString("href=\"/document/versions/" + nextId + "\"")));
    filter.setStatus(DocumentStatus.DRAFT);
    assertTrue(documents.documentList(viewer.getId(), filter, 0).documents().isEmpty());
  }

  @Test
  void releaseManifestAndZipKeepOriginalVersionsFilesAndRecipientsAfterNewEdits() throws Exception {
    var designDraft = draft(DocumentCategory.DESIGN, "초도 설계서");
    byte[] original = "초도 승인 첨부파일".getBytes(StandardCharsets.UTF_8);
    Long originalFile =
        files.upload(
            admin.getNickname(),
            designDraft.getId(),
            new MockMultipartFile("file", "승인근거.txt", "text/plain", original));
    var design = approve(designDraft);
    approve(draft(DocumentCategory.TEST_REPORT, "시험 보고서"));
    approve(draft(DocumentCategory.SECURITY_REVIEW, "보안 보고서"));
    workflow.addCheck(tester.getNickname(), projectId, "배포 대상 확인");
    String fingerprint = workflow.fingerprint(tester.getNickname(), projectId);
    Set<Long> passed =
        new HashSet<>(
            checks.findByProjectIdAndActiveTrueOrderById(projectId).stream()
                .map(QualityCheck::getId)
                .toList());
    workflow.test(tester.getNickname(), projectId, fingerprint, passed, "원본 검증 적합", null);
    workflow.assess(security.getNickname(), projectId, fingerprint, true, "원본 보안 검토 적합");
    projects.changeStatus(owner.getNickname(), projectId, "review");
    Long releaseId =
        releases.publish(
            owner.getNickname(), projectId, fingerprint, Set.of(viewer.getNickname()), "초도 배포");

    Long nextId = documents.newVersion(design.getDocument().getId(), admin.getId(), design.getId());
    var copied = attachments.findByVersionIdOrderById(nextId).getFirst();
    files.delete(admin.getNickname(), copied.getId());
    files.upload(
        admin.getNickname(),
        nextId,
        new MockMultipartFile(
            "file", "새근거.txt", "text/plain", "신규 초안".getBytes(StandardCharsets.UTF_8)));
    var next = versions.findById(nextId).orElseThrow();
    documents.edit(nextId, admin.getId(), next.getRevision(), "후속 설계 v2", "새로운 본문", false);

    var detail = releases.detailView(viewer.getNickname(), releaseId, 0);
    assertTrue(detail.downloadAllowed());
    assertFalse(detail.recallAllowed());
    assertEquals(1, detail.recipients().getTotalElements());
    assertEquals(viewer.getNickname(), detail.recipients().getContent().getFirst().getNickname());
    var fixedDesign =
        detail.documents().stream()
            .filter(d -> d.version().versionId().equals(design.getId()))
            .findFirst()
            .orElseThrow();
    assertEquals(1, fixedDesign.version().versionNumber());
    assertEquals("초도 설계서", fixedDesign.version().title());
    assertEquals(originalFile, fixedDesign.files().getFirst().id());
    assertEquals("승인근거.txt", fixedDesign.files().getFirst().filename());
    assertFalse(detail.documents().stream().anyMatch(d -> d.version().versionId().equals(nextId)));
    mvc.perform(get("/releases/" + releaseId).with(user(viewer.getNickname())))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("승인근거.txt")))
        .andExpect(content().string(containsString(fixedDesign.files().getFirst().packagePath())))
        .andExpect(content().string(not(containsString("후속 설계 v2"))))
        .andExpect(content().string(not(containsString("새근거.txt"))))
        .andExpect(content().string(not(containsString("배포 회수 · 책임자 전용"))));
    var ownerDetail = releases.detailView(owner.getNickname(), releaseId, 0);
    assertFalse(ownerDetail.downloadAllowed());
    assertTrue(ownerDetail.recallAllowed());
    mvc.perform(get("/releases/" + releaseId).with(user(owner.getNickname())))
        .andExpect(status().isOk())
        .andExpect(
            content().string(not(containsString("href=\"/releases/" + releaseId + "/download\""))));

    var streaming =
        mvc.perform(get("/releases/" + releaseId + "/download").with(user(viewer.getNickname())))
            .andExpect(request().asyncStarted())
            .andReturn();
    byte[] zip =
        mvc.perform(asyncDispatch(streaming))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    Map<String, byte[]> contents = new HashMap<>();
    try (var input = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
      java.util.zip.ZipEntry entry;
      while ((entry = input.getNextEntry()) != null)
        contents.put(entry.getName(), input.readAllBytes());
    }
    Set<String> manifestPaths = new HashSet<>();
    detail
        .documents()
        .forEach(
            d -> {
              manifestPaths.add(d.packagePath());
              d.files().forEach(f -> manifestPaths.add(f.packagePath()));
            });
    assertEquals(manifestPaths, contents.keySet());
    assertArrayEquals(original, contents.get(fixedDesign.files().getFirst().packagePath()));
    assertTrue(
        new String(contents.get(fixedDesign.packagePath()), StandardCharsets.UTF_8)
            .contains("확정 대상 본문"));
    assertThrows(
        AccessDeniedException.class,
        () -> releases.detailView(outsider.getNickname(), releaseId, 0));

    releases.recall(owner.getNickname(), releaseId, "추가 검증 필요");
    assertFalse(releases.detailView(viewer.getNickname(), releaseId, 0).downloadAllowed());
    assertFalse(releases.detailView(owner.getNickname(), releaseId, 0).recallAllowed());
    mvc.perform(get("/releases/" + releaseId + "/download").with(user(viewer.getNickname())))
        .andExpect(status().isGone());
  }
}
