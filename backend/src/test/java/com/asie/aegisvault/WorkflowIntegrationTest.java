package com.asie.aegisvault;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.asie.aegisvault.Department.*;
import com.asie.aegisvault.Document.*;
import com.asie.aegisvault.User.*;
import com.asie.aegisvault.access.*;
import com.asie.aegisvault.account.*;
import com.asie.aegisvault.attachment.*;
import com.asie.aegisvault.audit.*;
import com.asie.aegisvault.notice.AnnouncementService;
import com.asie.aegisvault.notice.DepartmentNoticeRepository;
import com.asie.aegisvault.notice.DepartmentNoticeService;
import com.asie.aegisvault.notice.NoticeForm;
import com.asie.aegisvault.notification.*;
import com.asie.aegisvault.project.*;
import com.asie.aegisvault.release.*;
import com.asie.aegisvault.security.*;
import com.asie.aegisvault.workflow.*;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:workflow-suite;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
      "spring.jpa.hibernate.ddl-auto=validate",
      "logging.file.name=",
      "app.files.path=./build/test-files/workflow",
      "app.audit.key-file=./build/test-files/workflow-audit.key"
    })
@AutoConfigureMockMvc
class WorkflowIntegrationTest {
  @Autowired UserRepository users;
  @Autowired DepartmentRepository departments;
  @Autowired ProjectService projects;
  @Autowired ProjectRepository projectRepository;
  @Autowired ProjectMemberRepository members;
  @Autowired DocumentService documents;
  @Autowired DocumentVersionRepository versions;
  @Autowired DocumentRepository documentRepository;
  @Autowired AttachmentService attachments;
  @Autowired AttachmentRepository attachmentRepository;
  @Autowired FileStore store;
  @Autowired WorkflowService workflow;
  @Autowired QualityCheckRepository checks;
  @Autowired NonconformityRepository defects;
  @Autowired ReleaseService releases;
  @Autowired ReleaseRepository releaseRepository;
  @Autowired TemporaryAccessService access;
  @Autowired TemporaryAccessRepository grants;
  @Autowired AccountService accounts;
  @Autowired PasswordRecoveryRepository recoveries;
  @Autowired PasswordEncoder passwords;
  @Autowired DepartmentLifecycle lifecycle;
  @Autowired AnnouncementService announcements;
  @Autowired DepartmentNoticeService departmentNotices;
  @Autowired DepartmentNoticeRepository noticeRepository;
  @Autowired AuditChain audit;
  @Autowired AuditRecordRepository records;
  @Autowired ApplicationEventPublisher events;
  @Autowired NotificationRepository notifications;
  @Autowired NotificationService inbox;
  @Autowired MockMvc mvc;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired JdbcTemplate jdbc;
  private Department research, quality;
  private User owner, writer, engineer, tester, security, viewer, outsider, admin;
  private Long projectId;
  private TransactionTemplate tx;
  private static int sequence;

  @BeforeEach
  void setup() {
    tx = new TransactionTemplate(transactionManager);
    String prefix = "flow" + (++sequence) + "-";
    research = departments.saveAndFlush(new Department(prefix + "연구개발", "연구"));
    quality = departments.saveAndFlush(new Department(prefix + "품질", "품질"));
    owner = account(prefix + "owner", Position.MANAGER, research);
    writer = account(prefix + "writer", Position.STAFF, research);
    engineer = account(prefix + "engineer", Position.MANAGER, research);
    tester = account(prefix + "tester", Position.MANAGER, quality);
    security = account(prefix + "security", Position.MANAGER, quality);
    viewer = account(prefix + "viewer", Position.STAFF, quality);
    outsider = account(prefix + "outsider", Position.STAFF, quality);
    admin = account(prefix + "admin", Position.ADMIN, null);
    ProjectForm form = new ProjectForm();
    form.setName(prefix + "탐지 시스템");
    form.setDescription("연구개발 및 배포 검증");
    form.setDepartmentId(research.getId());
    projectId = projects.create(owner.getNickname(), form);
    for (var pair :
        List.of(
            new Object[] {writer, ProjectRole.CONTRIBUTOR},
            new Object[] {engineer, ProjectRole.ENGINEERING},
            new Object[] {tester, ProjectRole.QUALITY},
            new Object[] {security, ProjectRole.SECURITY},
            new Object[] {viewer, ProjectRole.VIEWER}))
      projects.assignMember(
          owner.getNickname(), projectId, ((User) pair[0]).getId(), (ProjectRole) pair[1]);
    projects.changeStatus(owner.getNickname(), projectId, "start");
  }

  private User account(String name, Position position, Department department) {
    User user = new User(name, name + "@example.test", passwords.encode("Test-password-42!"));
    user.assign(position, department);
    user.changeClearance(SecurityClassification.RESTRICTED);
    return users.saveAndFlush(user);
  }

  private DocumentVersion draft(DocumentCategory category, SecurityClassification classification) {
    Document doc =
        documents.create(
            new DocumentCommand(
                "설계 " + category,
                "검증 대상 본문",
                Position.STAFF,
                projectId,
                category,
                classification,
                true),
            writer.getId());
    return versions.findFirstByDocumentOrderByVersionNumberDesc(doc).orElseThrow();
  }

  private DocumentVersion approved(DocumentCategory category) {
    DocumentVersion version = draft(category, SecurityClassification.INTERNAL);
    documents.edit(
        version.getId(),
        writer.getId(),
        version.getRevision(),
        version.getTitle(),
        version.getContent(),
        true);
    documents.approve(version.getId(), engineer.getId());
    return versions.findById(version.getId()).orElseThrow();
  }

  private String completeEvidence() {
    for (var category :
        List.of(
            DocumentCategory.DESIGN,
            DocumentCategory.TEST_REPORT,
            DocumentCategory.SECURITY_REVIEW)) approved(category);
    workflow.addCheck(tester.getNickname(), projectId, "요구사항 추적성 확인");
    String fingerprint = workflow.fingerprint(tester.getNickname(), projectId);
    Set<Long> passed =
        new HashSet<>(
            checks.findByProjectIdAndActiveTrueOrderById(projectId).stream()
                .map(QualityCheck::getId)
                .toList());
    workflow.test(
        tester.getNickname(), projectId, fingerprint, passed, "장비 A, 시험 절차 T-01, 전체 항목 적합", null);
    workflow.assess(security.getNickname(), projectId, fingerprint, true, "접근 통제와 배포 범위 검증 완료");
    projects.changeStatus(owner.getNickname(), projectId, "review");
    return fingerprint;
  }

  @Test
  void adminSubmissionImmediatelyApprovesEveryClassificationIncludingProjectVersions() {
    for (var classification : SecurityClassification.values()) {
      Document doc =
          documents.create(
              new DocumentCommand(
                  "관리자 " + classification,
                  "확정 본문",
                  Position.STAFF,
                  projectId,
                  DocumentCategory.DESIGN,
                  classification,
                  false),
              admin.getId());
      var first = versions.findFirstByDocumentOrderByVersionNumberDesc(doc).orElseThrow();
      assertEquals(DocumentStatus.APPROVED, first.getStatus());
      assertEquals(admin.getId(), first.getReviewedBy().getId());
      assertEquals(
          "AUTO_APPROVED",
          documents
              .comments(first.getId(), admin.getId(), 0)
              .getContent()
              .getFirst()
              .getDecision());
      Long secondId = documents.newVersion(doc.getId(), admin.getId(), first.getId());
      var second = versions.findById(secondId).orElseThrow();
      assertEquals(DocumentStatus.DRAFT, second.getStatus());
      documents.edit(secondId, admin.getId(), second.getRevision(), "개정 확정", "v2 근거", true);
      assertEquals(DocumentStatus.APPROVED, versions.findById(secondId).orElseThrow().getStatus());
    }
    var draftDoc =
        documents.create(
            new DocumentCommand(
                "관리자 초안",
                "",
                Position.STAFF,
                projectId,
                DocumentCategory.OTHER,
                SecurityClassification.RESTRICTED,
                true),
            admin.getId());
    var draft = versions.findFirstByDocumentOrderByVersionNumberDesc(draftDoc).orElseThrow();
    assertEquals(DocumentStatus.DRAFT, draft.getStatus());
    assertTrue(documents.comments(draft.getId(), admin.getId(), 0).isEmpty());
    var gate = releases.gate(owner.getNickname(), projectId);
    assertFalse(gate.ready());
    assertTrue(gate.blockers().stream().anyMatch(reason -> reason.contains("품질")));
    assertTrue(gate.blockers().stream().anyMatch(reason -> reason.contains("보안")));
  }

  @Test
  void projectMembershipAndClassificationAreEnforcedForListHistoryAndFiles() throws Exception {
    var version = draft(DocumentCategory.DESIGN, SecurityClassification.CONFIDENTIAL);
    var file = new MockMultipartFile("file", "evidence.txt", "text/plain", "evidence".getBytes());
    Long attachmentId = attachments.upload(writer.getNickname(), version.getId(), file);
    assertThrows(
        AccessDeniedException.class,
        () -> documents.versionDetail(version.getId(), outsider.getId()));
    assertThrows(
        AccessDeniedException.class,
        () -> attachments.download(viewer.getNickname(), attachmentId));
    documents.edit(version.getId(), writer.getId(), version.getRevision(), "기밀 설계", "본문", true);
    documents.approve(version.getId(), engineer.getId());
    assertEquals(
        DocumentStatus.PENDING_SECURITY_APPROVAL,
        versions.findById(version.getId()).orElseThrow().getStatus());
    assertThrows(
        AccessDeniedException.class,
        () -> documents.review(version.getId(), engineer.getId(), true, true, "자기 검토"));
    documents.review(version.getId(), security.getId(), true, true, "보안 검토 완료");
    assertEquals(version.getId(), documents.versionDetail(version.getId(), viewer.getId()).getId());
    assertEquals(
        "evidence.txt", attachments.download(viewer.getNickname(), attachmentId).getFilename());
    tx.executeWithoutResult(
        t ->
            users
                .findById(viewer.getId())
                .orElseThrow()
                .changeClearance(SecurityClassification.INTERNAL));
    assertThrows(
        AccessDeniedException.class,
        () -> documents.versionDetail(version.getId(), viewer.getId()));
    var filter = new DocumentFilter();
    filter.setProjectId(projectId);
    assertTrue(documents.documentList(viewer.getId(), filter, 0).documents().isEmpty());
    mvc.perform(get("/files/" + attachmentId).with(user(outsider.getNickname())))
        .andExpect(status().isForbidden());
  }

  @Test
  void versionsRemainImmutableAndAttachmentCopiesKeepTheirBytes() throws Exception {
    var version = draft(DocumentCategory.DESIGN, SecurityClassification.INTERNAL);
    Long fileId =
        attachments.upload(
            writer.getNickname(),
            version.getId(),
            new MockMultipartFile(
                "file", "trace.txt", "text/plain", "original evidence".getBytes()));
    documents.edit(version.getId(), writer.getId(), version.getRevision(), "제목", "본문", true);
    assertThrows(
        ResponseStatusException.class, () -> attachments.delete(writer.getNickname(), fileId));
    documents.approve(version.getId(), engineer.getId());
    Long next =
        documents.newVersion(version.getDocument().getId(), writer.getId(), version.getId());
    assertEquals(2, versions.findById(next).orElseThrow().getVersionNumber());
    assertThrows(
        ResponseStatusException.class,
        () -> documents.newVersion(version.getDocument().getId(), writer.getId(), version.getId()));
    var copy = attachmentRepository.findByVersionIdOrderById(next).getFirst();
    attachments.delete(writer.getNickname(), copy.getId());
    assertTrue(store.intact(attachmentRepository.findById(fileId).orElseThrow()));
    assertEquals("본문", documents.versionDetail(version.getId(), viewer.getId()).getContent());
    assertThrows(AccessDeniedException.class, () -> documents.versionDetail(next, viewer.getId()));
    assertEquals(
        1, documents.history(version.getDocument().getId(), viewer.getId(), 0).getTotalElements());
    var latest = versions.findById(next).orElseThrow();
    documents.edit(next, writer.getId(), latest.getRevision(), "개정", "개정 본문", false);
    assertThrows(
        ResponseStatusException.class,
        () -> documents.edit(next, writer.getId(), latest.getRevision(), "낡은 화면", "덮어쓰기", false));
  }

  @Test
  void releaseRequiresCurrentEvidenceAndRecallStopsRecipientDownloads() throws Exception {
    assertFalse(releases.gate(owner.getNickname(), projectId).ready());
    String fingerprint = completeEvidence();
    assertTrue(releases.gate(owner.getNickname(), projectId).ready());
    Long releaseId =
        releases.publish(
            owner.getNickname(), projectId, fingerprint, Set.of(viewer.getNickname()), "초도 배포");
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
    try (var input =
        new java.util.zip.ZipInputStream(
            new java.io.ByteArrayInputStream(zip), java.nio.charset.StandardCharsets.UTF_8)) {
      int count = 0;
      java.util.zip.ZipEntry entry;
      while ((entry = input.getNextEntry()) != null) {
        String html = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.contains(viewer.getNickname()));
        assertTrue(html.contains("watermark"));
        count++;
      }
      assertEquals(3, count);
    }
    mvc.perform(get("/releases/" + releaseId).with(user(owner.getNickname())))
        .andExpect(status().isOk());
    var snapshot = releases.downloadVersions(viewer.getNickname(), releaseId);
    assertEquals(3, snapshot.size());
    assertThrows(
        AccessDeniedException.class,
        () -> releases.downloadVersions(writer.getNickname(), releaseId));
    var original = versions.findById(snapshot.getFirst()).orElseThrow();
    documents.newVersion(original.getDocument().getId(), writer.getId(), original.getId());
    assertFalse(releases.gate(owner.getNickname(), projectId).ready());
    assertEquals(snapshot, releases.downloadVersions(viewer.getNickname(), releaseId));
    releases.recall(owner.getNickname(), releaseId, "후속 결함 조사");
    assertEquals(
        410,
        assertThrows(
                ResponseStatusException.class,
                () -> releases.downloadVersions(viewer.getNickname(), releaseId))
            .getStatusCode()
            .value());
    assertTrue(notifications.countByRecipientIdAndReadAtIsNull(viewer.getId()) >= 2);
  }

  @Test
  void changedDocumentCannotBeReleasedUntilBothIndependentReviewsCoverTheNewVersion()
      throws Exception {
    String firstFingerprint = completeEvidence();
    var originalGate = releases.gate(owner.getNickname(), projectId);
    assertTrue(originalGate.ready());
    assertEquals(ReviewEvidence.State.CURRENT, originalGate.qualityReview().state());
    assertEquals(ReviewEvidence.State.CURRENT, originalGate.securityReview().state());
    assertTrue(originalGate.qualityReview().baselineAvailable());
    assertTrue(originalGate.securityReview().baselineAvailable());
    assertEquals(firstFingerprint, originalGate.snapshot().fingerprint());
    var originalQuality = workflow.runs(owner.getNickname(), projectId, 0).getContent().getFirst();
    var originalSecurity =
        workflow.assessments(owner.getNickname(), projectId, 0).getContent().getFirst();
    var originalDesign =
        originalGate.snapshot().versions().stream()
            .filter(version -> version.category() == DocumentCategory.DESIGN)
            .findFirst()
            .orElseThrow();

    // Opening the gate and review histories must not invalidate completed evidence.
    workflow.runs(owner.getNickname(), projectId, 0);
    workflow.assessments(owner.getNickname(), projectId, 0);
    var unchangedGate = releases.gate(owner.getNickname(), projectId);
    assertTrue(unchangedGate.ready());
    assertEquals(firstFingerprint, unchangedGate.snapshot().fingerprint());
    assertEquals(originalQuality.getId(), unchangedGate.qualityRunId());
    assertEquals(originalSecurity.getId(), unchangedGate.securityAssessmentId());
    assertEquals(ReviewEvidence.State.CURRENT, unchangedGate.qualityReview().state());
    assertEquals(ReviewEvidence.State.CURRENT, unchangedGate.securityReview().state());
    assertTrue(unchangedGate.qualityReview().changes().isEmpty());
    assertTrue(unchangedGate.securityReview().changes().isEmpty());

    Long revisedId =
        documents.newVersion(
            originalDesign.documentId(), admin.getId(), originalDesign.versionId());
    var revision = versions.findById(revisedId).orElseThrow();
    assertEquals(DocumentStatus.DRAFT, revision.getStatus());
    assertEquals(
        ProjectStatus.ACTIVE, projectRepository.findById(projectId).orElseThrow().getStatus());
    assertFalse(releases.gate(owner.getNickname(), projectId).ready());
    documents.edit(
        revisedId, admin.getId(), revision.getRevision(), "열람 제한 초안 제목", "작성 중인 변경 사항", false);
    for (User restrictedReader : List.of(tester, security)) {
      mvc.perform(
              get("/projects/" + projectId + "/work")
                  .with(user(restrictedReader.getNickname()))
                  .param("tab", "release"))
          .andExpect(status().isOk())
          .andExpect(content().string(containsString("현재 v2 · 열람 제한")))
          .andExpect(content().string(containsString("검증 당시 v1")))
          .andExpect(content().string(not(containsString("열람 제한 초안 제목"))))
          .andExpect(
              content()
                  .string(not(containsString("href=\"/document/versions/" + revisedId + "\""))));
    }
    mvc.perform(
            get("/projects/" + projectId + "/work")
                .with(user(admin.getNickname()))
                .param("tab", "release"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("열람 제한 초안 제목")))
        .andExpect(
            content().string(containsString("href=\"/document/versions/" + revisedId + "\"")));
    revision = versions.findById(revisedId).orElseThrow();
    documents.edit(
        revisedId, admin.getId(), revision.getRevision(), "개정 설계 v2", "설계 허용 오차 변경", true);
    assertEquals(DocumentStatus.APPROVED, versions.findById(revisedId).orElseThrow().getStatus());
    assertEquals(
        DocumentStatus.APPROVED,
        versions.findById(originalDesign.versionId()).orElseThrow().getStatus());

    // Request review again so the evidence checks, rather than project status, reject release.
    projects.changeStatus(owner.getNickname(), projectId, "review");
    var changedGate = releases.gate(owner.getNickname(), projectId);
    String secondFingerprint = changedGate.snapshot().fingerprint();
    assertNotEquals(firstFingerprint, secondFingerprint);
    assertFalse(changedGate.ready());
    assertEquals(ReviewEvidence.State.STALE, changedGate.qualityReview().state());
    assertEquals(ReviewEvidence.State.STALE, changedGate.securityReview().state());
    assertEquals(1, changedGate.qualityReview().changes().size());
    var changedDesign = changedGate.qualityReview().changes().getFirst();
    assertEquals(originalDesign.documentId(), changedDesign.documentId());
    assertEquals(originalDesign.versionId(), changedDesign.previousVersionId());
    assertEquals(1, changedDesign.previousNumber());
    assertEquals(revisedId, changedDesign.currentVersionId());
    assertEquals(2, changedDesign.currentNumber());
    assertEquals(changedGate.qualityReview().changes(), changedGate.securityReview().changes());
    mvc.perform(
            get("/projects/" + projectId + "/work")
                .with(user(owner.getNickname()))
                .param("tab", "release"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("문서 변경과 검증 상태")))
        .andExpect(content().string(containsString("검증 당시 v1")))
        .andExpect(content().string(containsString("현재 v2")))
        .andExpect(content().string(containsString("개정 설계 v2")))
        .andExpect(content().string(containsString("tab=quality#quality-review")))
        .andExpect(content().string(containsString("tab=security#security-review")))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("배포 확정</button>"))));
    mvc.perform(
            get("/projects/" + projectId + "/work")
                .with(user(tester.getNickname()))
                .param("tab", "release"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("개정 설계 v2")))
        .andExpect(
            content().string(containsString("href=\"/document/versions/" + revisedId + "\"")));
    mvc.perform(
            post("/projects/" + projectId + "/work/release")
                .with(user(owner.getNickname()))
                .with(csrf())
                .param("fingerprint", firstFingerprint)
                .param("recipientNames", viewer.getNickname())
                .param("notes", "변경 전 화면에서 배포 시도"))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/projects/" + projectId + "/work/release")
                .with(user(owner.getNickname()))
                .with(csrf())
                .param("fingerprint", secondFingerprint)
                .param("recipientNames", viewer.getNickname())
                .param("notes", "현재 구성으로 이전 시험 결과 재사용 시도"))
        .andExpect(status().isBadRequest());
    assertEquals(0, releases.list(owner.getNickname(), projectId, 0).getTotalElements());

    Set<Long> passedChecks =
        new HashSet<>(
            checks.findByProjectIdAndActiveTrueOrderById(projectId).stream()
                .map(QualityCheck::getId)
                .toList());
    Long revisedQualityId =
        workflow.test(
            tester.getNickname(),
            projectId,
            secondFingerprint,
            passedChecks,
            "v2 허용 오차 검증 적합",
            null);
    projects.changeStatus(owner.getNickname(), projectId, "review");
    var qualityOnlyGate = releases.gate(owner.getNickname(), projectId);
    assertFalse(qualityOnlyGate.ready());
    assertEquals(revisedQualityId, qualityOnlyGate.qualityRunId());
    assertEquals(originalSecurity.getId(), qualityOnlyGate.securityAssessmentId());
    assertEquals(ReviewEvidence.State.CURRENT, qualityOnlyGate.qualityReview().state());
    assertEquals(ReviewEvidence.State.STALE, qualityOnlyGate.securityReview().state());
    assertTrue(qualityOnlyGate.qualityReview().changes().isEmpty());
    assertEquals(
        changedGate.securityReview().changes(), qualityOnlyGate.securityReview().changes());
    mvc.perform(
            post("/projects/" + projectId + "/work/release")
                .with(user(owner.getNickname()))
                .with(csrf())
                .param("fingerprint", secondFingerprint)
                .param("recipientNames", viewer.getNickname())
                .param("notes", "품질시험만 갱신한 구성의 배포 시도"))
        .andExpect(status().isBadRequest());
    assertEquals(0, releases.list(owner.getNickname(), projectId, 0).getTotalElements());

    workflow.assess(security.getNickname(), projectId, secondFingerprint, true, "v2 보안 영향 재검토 완료");
    projects.changeStatus(owner.getNickname(), projectId, "review");
    var readyGate = releases.gate(owner.getNickname(), projectId);
    assertTrue(readyGate.ready());
    assertEquals(secondFingerprint, readyGate.snapshot().fingerprint());
    assertNotEquals(originalSecurity.getId(), readyGate.securityAssessmentId());
    assertEquals(ReviewEvidence.State.CURRENT, readyGate.qualityReview().state());
    assertEquals(ReviewEvidence.State.CURRENT, readyGate.securityReview().state());
    assertTrue(readyGate.qualityReview().changes().isEmpty());
    assertTrue(readyGate.securityReview().changes().isEmpty());
    mvc.perform(
            post("/projects/" + projectId + "/work/release")
                .with(user(owner.getNickname()))
                .with(csrf())
                .param("fingerprint", secondFingerprint)
                .param("recipientNames", viewer.getNickname())
                .param("notes", "v2 품질 및 보안 검증 후 배포"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("/releases/*"));
    assertEquals(1, releases.list(owner.getNickname(), projectId, 0).getTotalElements());
    var published =
        releaseRepository.findFirstByProjectIdOrderByReleaseNumberDesc(projectId).orElseThrow();
    assertEquals(secondFingerprint, published.getFingerprint());
    assertEquals(revisedQualityId, published.getQualityRunId());
    assertEquals(readyGate.securityAssessmentId(), published.getSecurityAssessmentId());
    var deliveredVersions = releases.downloadVersions(viewer.getNickname(), published.getId());
    assertTrue(deliveredVersions.contains(revisedId));
    assertFalse(deliveredVersions.contains(originalDesign.versionId()));

    // Superseded results remain historical records with their original outcome and evidence.
    var qualityHistory = workflow.runs(owner.getNickname(), projectId, 0);
    assertEquals(2, qualityHistory.getTotalElements());
    var retainedQuality =
        qualityHistory.stream()
            .filter(run -> run.getId().equals(originalQuality.getId()))
            .findFirst()
            .orElseThrow();
    assertTrue(retainedQuality.isPassed());
    assertEquals(firstFingerprint, retainedQuality.getFingerprint());
    assertEquals(originalQuality.getEvidence(), retainedQuality.getEvidence());
    assertEquals(originalQuality.getCreatedAt(), retainedQuality.getCreatedAt());
    var securityHistory = workflow.assessments(owner.getNickname(), projectId, 0);
    assertEquals(2, securityHistory.getTotalElements());
    var retainedSecurity =
        securityHistory.stream()
            .filter(assessment -> assessment.getId().equals(originalSecurity.getId()))
            .findFirst()
            .orElseThrow();
    assertTrue(retainedSecurity.isApproved());
    assertEquals(firstFingerprint, retainedSecurity.getFingerprint());
    assertEquals(originalSecurity.getFindings(), retainedSecurity.getFindings());
    assertEquals(originalSecurity.getCreatedAt(), retainedSecurity.getCreatedAt());
  }

  @Test
  void failedQualityRequiresExplicitRetestAndCorrectiveAction() {
    approved(DocumentCategory.DESIGN);
    workflow.addCheck(tester.getNickname(), projectId, "추적성 확인");
    String fingerprint = workflow.fingerprint(tester.getNickname(), projectId);
    Long failed =
        workflow.test(
            tester.getNickname(), projectId, fingerprint, Set.of(), "요구사항 R2 증빙 누락", null);
    var defect =
        defects
            .findByProjectId(projectId, org.springframework.data.domain.PageRequest.of(0, 20))
            .getContent()
            .getFirst();
    var check = checks.findByProjectIdAndActiveTrueOrderById(projectId).getFirst();
    Long unrelated =
        workflow.test(
            tester.getNickname(), projectId, fingerprint, Set.of(check.getId()), "증빙 보완 확인", null);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            workflow.closeDefect(
                tester.getNickname(), projectId, defect.getId(), unrelated, "분석 완료"));
    Long retest =
        workflow.test(
            tester.getNickname(),
            projectId,
            fingerprint,
            Set.of(check.getId()),
            "R2 근거 재확인",
            failed);
    workflow.closeDefect(
        tester.getNickname(), projectId, defect.getId(), retest, "원인: 근거 연결 누락. 시정: 검토 절차 강화.");
    assertEquals(0, defects.countByProjectIdAndClosedFalse(projectId));
  }

  @Test
  void engineeringResolutionMustUseApprovedSuccessorOfTheSameDocument() {
    var base = approved(DocumentCategory.DESIGN);
    var unrelated = approved(DocumentCategory.OTHER);
    Long changeId =
        workflow.requestChange(writer.getNickname(), projectId, "회로 수정", "요구 조건 변경", base.getId());
    workflow.assignChange(owner.getNickname(), projectId, changeId, engineer.getId());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            workflow.resolveChange(
                engineer.getNickname(), projectId, changeId, unrelated.getId(), "완료", false));
    Long next = documents.newVersion(base.getDocument().getId(), writer.getId(), base.getId());
    var draft = versions.findById(next).orElseThrow();
    documents.edit(next, writer.getId(), draft.getRevision(), draft.getTitle(), "회로 수정 반영", true);
    documents.approve(next, engineer.getId());
    workflow.resolveChange(
        engineer.getNickname(), projectId, changeId, next, "요구 조건 반영 및 검토 완료", false);
    assertEquals(
        EngineeringChange.Status.RESOLVED,
        workflow.changes(owner.getNickname(), projectId, 0).getContent().getFirst().getStatus());
  }

  @Test
  void temporaryAccessExpiresAndNeverGivesEditingOrReviewPrivileges() {
    var version = approved(DocumentCategory.DESIGN);
    Long documentId = version.getDocument().getId();
    access.request(outsider.getNickname(), documentId, "협력 검토", Instant.now().plusSeconds(3600));
    var request = access.mine(outsider.getNickname(), 0).getContent().getFirst();
    assertThrows(
        AccessDeniedException.class,
        () ->
            access.decide(
                outsider.getNickname(),
                request.getId(),
                true,
                Instant.now().plusSeconds(300),
                "본인 승인"));
    access.decide(
        owner.getNickname(), request.getId(), true, Instant.now().plusSeconds(600), "업무 범위 확인");
    assertEquals(
        version.getId(), documents.versionDetail(version.getId(), outsider.getId()).getId());
    assertThrows(
        AccessDeniedException.class,
        () -> documents.newVersion(documentId, outsider.getId(), version.getId()));
    jdbc.update(
        "update temporary_access set expires_at=? where id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        request.getId());
    assertThrows(
        AccessDeniedException.class,
        () -> documents.versionDetail(version.getId(), outsider.getId()));
    assertTrue(access.mine(outsider.getNickname(), 0).getContent().getFirst().isExpired());
  }

  @Test
  void recoveryTokensAreSingleUseAndPasswordChangeInvalidatesExistingSessions() throws Exception {
    mvc.perform(
            post("/user/login")
                .with(csrf())
                .param("username", writer.getNickname())
                .param("password", "Test-password-42!"))
        .andExpect(status().is3xxRedirection());
    var session =
        (org.springframework.mock.web.MockHttpSession)
            mvc.perform(
                    post("/user/login")
                        .with(csrf())
                        .param("username", writer.getNickname())
                        .param("password", "Test-password-42!"))
                .andReturn()
                .getRequest()
                .getSession(false);
    accounts.request(writer.getNickname(), writer.getEmail());
    var recovery = recoveries.findByUserIdAndCompletedAtIsNull(writer.getId()).getFirst();
    String token = accounts.issue(admin.getNickname(), recovery.getId(), "대면 신원 확인");
    assertNotEquals(token, recoveries.findById(recovery.getId()).orElseThrow().getTokenHash());
    accounts.reset(token, "New-password-43!", "New-password-43!");
    assertThrows(
        IllegalArgumentException.class,
        () -> accounts.reset(token, "Another-password-44!", "Another-password-44!"));
    mvc.perform(get("/").session(session)).andExpect(status().isForbidden());
    assertTrue(
        passwords.matches(
            "New-password-43!", users.findById(writer.getId()).orElseThrow().getPassword()));
  }

  @Test
  void lostDocumentClearanceBlocksDirectWorkflowMutationsAsWellAsPages() throws Exception {
    var version = draft(DocumentCategory.DESIGN, SecurityClassification.CONFIDENTIAL);
    documents.edit(
        version.getId(),
        writer.getId(),
        version.getRevision(),
        version.getTitle(),
        "보호 대상 근거",
        true);
    documents.approve(version.getId(), engineer.getId());
    documents.review(version.getId(), security.getId(), true, true, "보안 검토 완료");
    workflow.addCheck(tester.getNickname(), projectId, "기밀 문서 추적성");
    String fingerprint = workflow.fingerprint(tester.getNickname(), projectId);
    Long failed =
        workflow.test(tester.getNickname(), projectId, fingerprint, Set.of(), "기밀 시험 부적합", null);
    var defect =
        defects
            .findByProjectId(projectId, org.springframework.data.domain.PageRequest.of(0, 20))
            .getContent()
            .getFirst();
    var check = checks.findByProjectIdAndActiveTrueOrderById(projectId).getFirst();
    Long passed =
        workflow.test(
            tester.getNickname(),
            projectId,
            fingerprint,
            Set.of(check.getId()),
            "기밀 재시험 적합",
            failed);
    tx.executeWithoutResult(
        t ->
            users
                .findById(tester.getId())
                .orElseThrow()
                .changeClearance(SecurityClassification.INTERNAL));
    String path = "/projects/" + projectId + "/work";
    mvc.perform(get(path).param("tab", "defects").with(user(tester.getNickname())))
        .andExpect(status().isForbidden());
    mvc.perform(get(path).param("tab", "release").with(user(tester.getNickname())))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(path + "/defects/" + defect.getId() + "/close")
                .with(user(tester.getNickname()))
                .with(csrf())
                .param("passingRunId", passed.toString())
                .param("correctiveAction", "직접 요청으로 종료 시도"))
        .andExpect(status().isForbidden());
    assertFalse(defects.findById(defect.getId()).orElseThrow().isClosed());
    mvc.perform(
            post(path + "/checks/" + check.getId() + "/retire")
                .with(user(tester.getNickname()))
                .with(csrf()))
        .andExpect(status().isForbidden());
    assertTrue(checks.findById(check.getId()).orElseThrow().isActive());
    mvc.perform(
            post(path + "/checks")
                .with(user(tester.getNickname()))
                .with(csrf())
                .param("criterion", "변경 시도"))
        .andExpect(status().isForbidden());
    assertEquals(1, checks.findByProjectIdAndActiveTrueOrderById(projectId).size());
  }

  @Test
  void directPasswordChangeRevokesPreviouslyIssuedRecoveryLinks() {
    accounts.request(writer.getNickname(), writer.getEmail());
    var recovery = recoveries.findByUserIdAndCompletedAtIsNull(writer.getId()).getFirst();
    String token = accounts.issue(admin.getNickname(), recovery.getId(), "본인 확인 완료");
    accounts.change(
        writer.getNickname(), "Test-password-42!", "Changed-password-43!", "Changed-password-43!");
    assertThrows(
        IllegalArgumentException.class,
        () -> accounts.reset(token, "Unwanted-password-44!", "Unwanted-password-44!"));
    assertTrue(recoveries.findByUserIdAndCompletedAtIsNull(writer.getId()).isEmpty());
    assertTrue(
        passwords.matches(
            "Changed-password-43!", users.findById(writer.getId()).orElseThrow().getPassword()));
  }

  @Test
  void rejectedPasswordChangeKeepsTheRecoveryLinkUsable() {
    accounts.request(writer.getNickname(), writer.getEmail());
    var recovery = recoveries.findByUserIdAndCompletedAtIsNull(writer.getId()).getFirst();
    String token = accounts.issue(admin.getNickname(), recovery.getId(), "본인 확인 완료");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            accounts.change(
                writer.getNickname(),
                "wrong-password",
                "Changed-password-43!",
                "Changed-password-43!"));
    accounts.reset(token, "Recovered-password-44!", "Recovered-password-44!");
    assertTrue(
        passwords.matches(
            "Recovered-password-44!", users.findById(writer.getId()).orElseThrow().getPassword()));
  }

  @Test
  void departmentTransferKeepsAnnouncementsReadableToTransferredMembers() throws Exception {
    var form = new NoticeForm();
    form.setTitle("이관 후에도 필요한 업무 공지");
    form.setContent("기존 부서의 업무 절차와 근거");
    Long noticeId = departmentNotices.create(owner.getNickname(), research.getId(), form);
    var before = departmentNotices.detail(owner.getNickname(), research.getId(), noticeId);
    Long globalId = announcements.create(admin.getNickname(), "전체 공지", "공통 안내");
    lifecycle.close(admin.getNickname(), research.getId(), quality.getId(), research.getRevision());
    var after = departmentNotices.detail(writer.getNickname(), quality.getId(), noticeId);
    assertEquals(before.title(), after.title());
    assertEquals(before.content(), after.content());
    assertEquals(before.authorName(), after.authorName());
    assertEquals(before.createdAt(), after.createdAt());
    assertEquals(quality.getId(), after.departmentId());
    mvc.perform(get("/departments/" + quality.getId()).with(user(writer.getNickname())))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(form.getTitle())));
    assertEquals("전체 공지", announcements.detail(writer.getNickname(), globalId).getTitle());
    assertNull(noticeRepository.findById(globalId).orElseThrow().getDepartment());
  }

  @Test
  void aDepartmentContainingOnlyNoticesStillRequiresATransferTarget() {
    var source = departments.saveAndFlush(new Department("공지 보존 " + UUID.randomUUID(), "과거 업무 공지"));
    var form = new NoticeForm();
    form.setTitle("보존할 공지");
    form.setContent("공지 이력을 유실하지 않아야 합니다.");
    Long noticeId = departmentNotices.create(admin.getNickname(), source.getId(), form);
    assertThrows(
        IllegalArgumentException.class,
        () -> lifecycle.close(admin.getNickname(), source.getId(), null, source.getRevision()));
    assertFalse(departments.findById(source.getId()).orElseThrow().isClosed());
    lifecycle.close(admin.getNickname(), source.getId(), quality.getId(), source.getRevision());
    assertEquals(
        "보존할 공지",
        departmentNotices.detail(tester.getNickname(), quality.getId(), noticeId).title());
  }

  @Test
  void departmentCloseTransfersWorkWithoutDeletingItsHistory() {
    var version = approved(DocumentCategory.DESIGN);
    var source = departments.findById(research.getId()).orElseThrow();
    lifecycle.close(admin.getNickname(), research.getId(), quality.getId(), source.getRevision());
    assertTrue(departments.findById(research.getId()).orElseThrow().isClosed());
    assertEquals(
        quality.getId(), users.findById(writer.getId()).orElseThrow().getDepartment().getId());
    assertEquals(
        quality.getId(),
        documentRepository
            .findById(version.getDocument().getId())
            .orElseThrow()
            .getDepartment()
            .getId());
    assertEquals(
        quality.getId(),
        projectRepository.findById(projectId).orElseThrow().getDepartment().getId());
    assertTrue(versions.existsById(version.getId()));
  }

  @Test
  void businessRollbackDoesNotPublishAuditOrNotificationsAndTamperingIsDetected() {
    long count = records.count();
    tx.executeWithoutResult(
        t -> {
          events.publishEvent(
              AuditEvent.of(owner, "ROLLBACK_PROBE", "PROJECT", projectId, projectId, "롤백 확인")
                  .notify(List.of(viewer.getId()), "/projects/" + projectId));
          t.setRollbackOnly();
        });
    assertEquals(count, records.count());
    assertTrue(audit.verify().valid());
    var record =
        records
            .findAll(
                org.springframework.data.domain.PageRequest.of(
                    0,
                    1,
                    org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "id")))
            .getContent()
            .getFirst();
    String original = record.getDescription();
    jdbc.update("update audit_record set description=? where id=?", "변조", record.getId());
    assertFalse(audit.verify().valid());
    jdbc.update("update audit_record set description=? where id=?", original, record.getId());
    assertTrue(audit.verify().valid());
  }

  @Test
  void notificationOwnershipAndAnnouncementScopeAreEnforced() throws Exception {
    Long announcement = announcements.create(admin.getNickname(), "전체 일정", "공유 내용");
    assertEquals("전체 일정", announcements.detail(outsider.getNickname(), announcement).getTitle());
    assertThrows(
        AccessDeniedException.class, () -> announcements.create(writer.getNickname(), "위조", "내용"));
    var notification = inbox.list(writer.getNickname(), true, 0).getContent().getFirst();
    assertThrows(
        ResponseStatusException.class,
        () -> inbox.read(outsider.getNickname(), notification.getId()));
    assertTrue(inbox.read(writer.getNickname(), notification.getId()).startsWith("/projects/"));
  }

  @Test
  void allFeaturePagesRenderAndMutationRoutesRequireCsrf() throws Exception {
    var version = approved(DocumentCategory.DESIGN);
    Long announcement = announcements.create(admin.getNickname(), "전체 공지", "안내");
    for (String path :
        List.of(
            "/",
            "/document/list",
            "/document/new?projectId=" + projectId,
            "/document/versions/" + version.getId(),
            "/document/" + version.getDocument().getId() + "/history",
            "/projects",
            "/projects/new",
            "/projects/" + projectId,
            "/projects/" + projectId + "/members",
            "/projects/" + projectId + "/timeline",
            "/projects/" + projectId + "/work?tab=changes",
            "/projects/" + projectId + "/work?tab=quality",
            "/projects/" + projectId + "/work?tab=defects",
            "/projects/" + projectId + "/work?tab=security",
            "/projects/" + projectId + "/work?tab=release",
            "/departments/" + research.getId(),
            "/announcements",
            "/announcements/" + announcement,
            "/notifications",
            "/tasks",
            "/access",
            "/account",
            "/admin/users",
            "/admin/departments",
            "/admin/recovery",
            "/admin/audit"))
      mvc.perform(get(path).with(user(admin.getNickname()).roles("ADMIN")))
          .andExpect(status().isOk())
          .andExpect(content().string(not(containsString("Exception"))));
    mvc.perform(
            post("/projects/" + projectId + "/status")
                .with(user(owner.getNickname()))
                .param("action", "review"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/account/recovery")).andExpect(status().isOk());
    mvc.perform(get("/account/reset").param("token", "not-a-valid-token"))
        .andExpect(status().isOk());
  }

  @Test
  void attachmentIntegrityAndUnsafeFileTypesBlockDownloadsAndUploads() {
    var version = draft(DocumentCategory.OTHER, SecurityClassification.INTERNAL);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            attachments.upload(
                writer.getNickname(),
                version.getId(),
                new MockMultipartFile(
                    "file", "run.html", "text/html", "<script>run()</script>".getBytes())));
    Long id =
        attachments.upload(
            writer.getNickname(),
            version.getId(),
            new MockMultipartFile("file", "proof.txt", "text/plain", "proof".getBytes()));
    try {
      Files.writeString(
          store.path(attachmentRepository.findById(id).orElseThrow().getStorageKey()), "modified");
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    assertThrows(
        ResponseStatusException.class, () -> attachments.download(writer.getNickname(), id));
  }

  @Test
  void concurrentNewVersionsCreateExactlyOneSuccessor() throws Exception {
    var base = approved(DocumentCategory.DESIGN);
    assertEquals(
        List.of(200, 409),
        concurrently(
            () -> documents.newVersion(base.getDocument().getId(), writer.getId(), base.getId()),
            () -> documents.newVersion(base.getDocument().getId(), writer.getId(), base.getId())));
    assertEquals(
        2, documents.history(base.getDocument().getId(), writer.getId(), 0).getTotalElements());
  }

  @Test
  void concurrentReleaseSubmissionsDoNotCreateTwoDeliveries() throws Exception {
    String fingerprint = completeEvidence();
    assertEquals(
        List.of(200, 409),
        concurrently(
            () ->
                releases.publish(
                    owner.getNickname(),
                    projectId,
                    fingerprint,
                    Set.of(viewer.getNickname()),
                    "확정"),
            () ->
                releases.publish(
                    owner.getNickname(),
                    projectId,
                    fingerprint,
                    Set.of(viewer.getNickname()),
                    "확정")));
    assertEquals(1, releases.list(owner.getNickname(), projectId, 0).getTotalElements());
  }

  @Test
  void concurrentRecoveryCannotConsumeTheSameTokenTwice() throws Exception {
    accounts.request(writer.getNickname(), writer.getEmail());
    var recovery = recoveries.findByUserIdAndCompletedAtIsNull(writer.getId()).getFirst();
    String token = accounts.issue(admin.getNickname(), recovery.getId(), "대면 본인 확인");
    assertEquals(
        List.of(200, 400),
        concurrently(
            () -> accounts.reset(token, "Concurrent-first-42!", "Concurrent-first-42!"),
            () -> accounts.reset(token, "Concurrent-second-42!", "Concurrent-second-42!")));
  }

  private List<Integer> concurrently(Runnable first, Runnable second) throws Exception {
    var ready = new java.util.concurrent.CountDownLatch(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var one = executor.submit(() -> runTogether(first, ready, start));
      var two = executor.submit(() -> runTogether(second, ready, start));
      assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
      start.countDown();
      return java.util.stream.Stream.of(
              one.get(20, java.util.concurrent.TimeUnit.SECONDS),
              two.get(20, java.util.concurrent.TimeUnit.SECONDS))
          .sorted()
          .toList();
    }
  }

  private int runTogether(
      Runnable task,
      java.util.concurrent.CountDownLatch ready,
      java.util.concurrent.CountDownLatch start)
      throws Exception {
    ready.countDown();
    assertTrue(start.await(10, java.util.concurrent.TimeUnit.SECONDS));
    try {
      task.run();
      return 200;
    } catch (ResponseStatusException exception) {
      return exception.getStatusCode().value();
    } catch (IllegalArgumentException exception) {
      return 400;
    }
  }

  @Test
  void staleEvidenceAndReviewerIndependenceCannotBeBypassed() {
    var approved = approved(DocumentCategory.DESIGN);
    workflow.addCheck(tester.getNickname(), projectId, "검증");
    String fingerprint = workflow.fingerprint(tester.getNickname(), projectId);
    projects.assignMember(owner.getNickname(), projectId, writer.getId(), ProjectRole.QUALITY);
    assertThrows(
        AccessDeniedException.class,
        () ->
            workflow.test(
                writer.getNickname(), projectId, fingerprint, Set.of(), "작성자 자체 판정", null));
    workflow.addCheck(tester.getNickname(), projectId, "추가 검증");
    assertThrows(
        ResponseStatusException.class,
        () -> workflow.test(tester.getNickname(), projectId, fingerprint, Set.of(), "이전 화면", null));
    assertThrows(
        IllegalArgumentException.class,
        () -> projects.removeMember(owner.getNickname(), projectId, owner.getId()));
  }
}
