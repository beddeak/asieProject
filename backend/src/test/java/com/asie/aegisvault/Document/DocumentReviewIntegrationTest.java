package com.asie.aegisvault.Document;

import static org.junit.jupiter.api.Assertions.*;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.Document.dto.DocumentListItem;
import com.asie.aegisvault.User.AccountStatus;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.activity.DocumentAction;
import com.asie.aegisvault.activity.DocumentActivity;
import com.asie.aegisvault.security.UserAccessPolicy;
import com.asie.aegisvault.support.SqlCapture;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest(
    showSql = false,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:aegisvault-document-access-test;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.open-in-view=false",
      "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.asie.aegisvault.support.SqlCapture",
      "logging.file.name="
    })
@Import({
  DocumentService.class,
  UserAccessPolicy.class,
  com.asie.aegisvault.security.DocumentAccess.class,
  DocumentLocks.class,
  com.asie.aegisvault.project.ProjectAccess.class
})
class DocumentReviewIntegrationTest {
  @Autowired private EntityManager entityManager;
  @Autowired private DocumentService service;
  @Autowired private DocumentVersionRepository versions;

  private Department department;
  private Department otherDepartment;
  private User author;
  private User peer;
  private User manager;
  private User admin;
  private Document document;
  private DocumentVersion pending;

  @BeforeEach
  void setUp() {
    department = new Department("연구개발", "문서 담당 부서");
    otherDepartment = new Department("인사", "다른 부서");
    entityManager.persist(department);
    entityManager.persist(otherDepartment);
    author = user("author", Position.STAFF, department);
    peer = user("peer", Position.STAFF, department);
    manager = user("manager", Position.MANAGER, department);
    admin = user("admin", Position.ADMIN, null);
    document = service.create("검토할 문서", "본문", Position.STAFF, author.getId());
    pending = versions.findFirstByDocumentOrderByVersionNumberDesc(document).orElseThrow();
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  void creationStartsAtVersionOneAndPendingReviewWithSelectedGrade() {
    assertEquals(1, pending.getVersionNumber());
    assertEquals(DocumentStatus.PENDING_REVIEW, pending.getStatus());
    assertEquals(Position.STAFF, document.getRequiredPosition());
    assertEquals(author.getId(), document.getAuthor().getId());
    assertEquals(department.getId(), document.getDepartment().getId());
  }

  @Test
  void gradeAboveAuthorIsRejectedWithoutSavingDocument() {
    long before = countDocuments();
    assertThrows(
        AccessDeniedException.class,
        () -> service.create("조작 문서", "본문", Position.MANAGER, author.getId()));
    assertEquals(before, countDocuments());
  }

  @Test
  void authorCanSelectOwnGradeOrLower() {
    Document same = service.create("과장 문서", "본문", Position.MANAGER, manager.getId());
    Document lower = service.create("사원 문서", "본문", Position.STAFF, manager.getId());
    assertEquals(Position.MANAGER, same.getRequiredPosition());
    assertEquals(Position.STAFF, lower.getRequiredPosition());
    assertFalse(service.assignablePositions(manager.getId()).contains(Position.EXECUTIVE));
    assertTrue(service.assignablePositions(manager.getId()).contains(Position.MANAGER));
  }

  @Test
  void pendingAndRejectedDocumentsAreHiddenFromPeersButVisibleToAuthorAndReviewer() {
    assertThrows(
        AccessDeniedException.class, () -> service.documentdetail(document.getId(), peer.getId()));
    assertEquals(pending.getId(), service.documentdetail(document.getId(), author.getId()).getId());
    assertEquals(
        pending.getId(), service.documentdetail(document.getId(), manager.getId()).getId());
    service.reject(pending.getId(), manager.getId(), "시험 근거 보완 필요");
    assertThrows(
        AccessDeniedException.class, () -> service.documentdetail(document.getId(), peer.getId()));
    assertEquals(
        DocumentStatus.REJECTED,
        service.documentdetail(document.getId(), author.getId()).getStatus());
  }

  @Test
  void approvalOpensDocumentToEligiblePeersAndRecordsReviewer() {
    service.approve(pending.getId(), manager.getId());
    entityManager.flush();
    entityManager.clear();
    DocumentVersion approved = service.documentdetail(document.getId(), peer.getId());
    assertEquals(DocumentStatus.APPROVED, approved.getStatus());
    assertEquals(manager.getId(), approved.getReviewedBy().getId());
    assertNotNull(approved.getReviewedAt());
    TestTransaction.end();
    assertEquals("manager", approved.getReviewedBy().getNickname());
  }

  @Test
  void staffCannotEnterReviewPageOrPostDecision() {
    assertThrows(AccessDeniedException.class, () -> service.reviewQueue(author.getId(), 0));
    assertThrows(
        AccessDeniedException.class, () -> service.reviewDetail(pending.getId(), author.getId()));
    assertThrows(
        AccessDeniedException.class, () -> service.approve(pending.getId(), author.getId()));
    assertThrows(
        AccessDeniedException.class,
        () -> service.reject(pending.getId(), author.getId(), "시험 근거 보완 필요"));
  }

  @Test
  void otherDepartmentManagerCannotReadReviewOrDecide() {
    User outsider = user("outsider", Position.MANAGER, otherDepartment);
    assertTrue(service.reviewQueue(outsider.getId(), 0).isEmpty());
    assertThrows(
        AccessDeniedException.class, () -> service.reviewDetail(pending.getId(), outsider.getId()));
    assertThrows(
        AccessDeniedException.class, () -> service.approve(pending.getId(), outsider.getId()));
    assertThrows(
        AccessDeniedException.class,
        () -> service.reject(pending.getId(), outsider.getId(), "시험 근거 보완 필요"));
  }

  @Test
  void managerCannotApproveOrRejectOwnDocument() {
    Document own = service.create("본인 문서", "본문", Position.MANAGER, manager.getId());
    DocumentVersion version =
        versions.findFirstByDocumentOrderByVersionNumberDesc(own).orElseThrow();
    assertThrows(
        AccessDeniedException.class, () -> service.reviewDetail(version.getId(), manager.getId()));
    assertThrows(
        AccessDeniedException.class, () -> service.approve(version.getId(), manager.getId()));
    assertThrows(
        AccessDeniedException.class,
        () -> service.reject(version.getId(), manager.getId(), "시험 근거 보완 필요"));
    assertTrue(
        service.reviewQueue(manager.getId(), 0).stream()
            .noneMatch(v -> v.id().equals(version.getId())));
  }

  @Test
  void adminCanReadAllDepartmentsAndOwnCreationIsAlreadyApproved() {
    assertEquals(pending.getId(), service.reviewDetail(pending.getId(), admin.getId()).getId());
    assertEquals(pending.getId(), service.documentdetail(document.getId(), admin.getId()).getId());
    User adminAuthor = user("admin-author", Position.ADMIN, otherDepartment);
    Document own = service.create("관리자 문서", "본문", Position.ADMIN, adminAuthor.getId());
    entityManager.flush();
    entityManager.clear();
    DocumentVersion version =
        versions.findFirstByDocumentOrderByVersionNumberDesc(own).orElseThrow();
    assertEquals(DocumentStatus.APPROVED, version.getStatus());
    assertEquals(adminAuthor.getId(), version.getReviewedBy().getId());
    assertNotNull(version.getReviewedAt());
    var activity =
        entityManager
            .createQuery(
                "select a from DocumentActivity a where a.documentId = :documentId order by a.id",
                DocumentActivity.class)
            .setParameter("documentId", own.getId())
            .getResultList();
    assertEquals(
        List.of(DocumentAction.CREATED, DocumentAction.APPROVED),
        activity.stream().map(DocumentActivity::getAction).toList());
    assertTrue(
        activity.stream()
            .allMatch(
                entry ->
                    entry.getActorId().equals(adminAuthor.getId())
                        && entry.getActorPosition() == Position.ADMIN
                        && entry.getVersionId().equals(version.getId())));
    assertTrue(
        service.reviewQueue(adminAuthor.getId(), 0).stream()
            .noneMatch(v -> v.id().equals(version.getId())));
    assertTrue(
        service.reviewQueue(admin.getId(), 0).stream()
            .noneMatch(v -> v.id().equals(version.getId())));
    assertTrue(
        service.reviewQueue(manager.getId(), 0).stream()
            .noneMatch(v -> v.id().equals(version.getId())));
    assertThrows(
        AccessDeniedException.class,
        () -> service.reviewDetail(version.getId(), adminAuthor.getId()));
    assertThrows(
        AccessDeniedException.class, () -> service.approve(version.getId(), adminAuthor.getId()));
    assertThrows(
        AccessDeniedException.class,
        () -> service.reject(version.getId(), adminAuthor.getId(), "사유"));
    assertEquals(
        HttpStatus.CONFLICT,
        assertThrows(
                ResponseStatusException.class,
                () -> service.reviewDetail(version.getId(), admin.getId()))
            .getStatusCode());
    assertEquals(
        HttpStatus.CONFLICT,
        assertThrows(
                ResponseStatusException.class,
                () -> service.approve(version.getId(), admin.getId()))
            .getStatusCode());
    assertEquals(
        HttpStatus.CONFLICT,
        assertThrows(
                ResponseStatusException.class,
                () -> service.reject(version.getId(), admin.getId(), "사유"))
            .getStatusCode());
    assertEquals(
        DocumentStatus.APPROVED, service.documentdetail(own.getId(), admin.getId()).getStatus());
  }

  @Test
  void reviewerMustMeetDocumentGradeAsWellAsManagerRank() {
    User executive = user("executive", Position.EXECUTIVE, department);
    Document confidential = service.create("임원 문서", "본문", Position.EXECUTIVE, executive.getId());
    DocumentVersion version =
        versions.findFirstByDocumentOrderByVersionNumberDesc(confidential).orElseThrow();
    assertThrows(
        AccessDeniedException.class,
        () -> service.documentdetail(confidential.getId(), manager.getId()));
    assertThrows(
        AccessDeniedException.class, () -> service.reviewDetail(version.getId(), manager.getId()));
    assertThrows(
        AccessDeniedException.class, () -> service.approve(version.getId(), manager.getId()));
    assertTrue(
        service.reviewQueue(manager.getId(), 0).stream()
            .noneMatch(v -> v.id().equals(version.getId())));
    service.approve(version.getId(), admin.getId());
    assertThrows(
        AccessDeniedException.class,
        () -> service.documentdetail(confidential.getId(), manager.getId()));
  }

  @Test
  void repeatedDecisionIsConflictAndDoesNotOverwriteFirstReviewer() {
    service.approve(pending.getId(), manager.getId());
    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class,
            () -> service.reject(pending.getId(), admin.getId(), "시험 근거 보완 필요"));
    assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    DocumentVersion version = versions.findById(pending.getId()).orElseThrow();
    assertEquals(DocumentStatus.APPROVED, version.getStatus());
    assertEquals(manager.getId(), version.getReviewedBy().getId());
  }

  @Test
  void olderPendingVersionCannotBeReviewed() {
    Document attached = entityManager.find(Document.class, document.getId());
    DocumentVersion newer = new DocumentVersion(attached, 2, "새 버전", "수정 본문");
    newer.submitForReview();
    entityManager.persist(newer);
    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class, () -> service.approve(pending.getId(), manager.getId()));
    assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    var queue = service.reviewQueue(manager.getId(), 0);
    assertTrue(queue.stream().noneMatch(v -> v.id().equals(pending.getId())));
    assertTrue(queue.stream().anyMatch(v -> v.id().equals(newer.getId())));
  }

  @Test
  void reviewQueueIsPagedAndExcludesCompletedDocuments() {
    for (int index = 0; index < 21; index++) {
      service.create("문서 " + index, "본문", Position.STAFF, author.getId());
    }
    var first = service.reviewQueue(manager.getId(), 0);
    var second = service.reviewQueue(manager.getId(), 1);
    assertEquals(20, first.getNumberOfElements());
    assertEquals(2, second.getNumberOfElements());
    assertEquals(22, first.getTotalElements());
    var last = service.reviewQueue(manager.getId(), Integer.MAX_VALUE);
    assertEquals(1, last.getNumber());
    assertEquals(second.getContent(), last.getContent());
    assertEquals(first.getContent(), service.reviewQueue(manager.getId(), -1).getContent());
    service.approve(pending.getId(), manager.getId());
    assertEquals(21, service.reviewQueue(manager.getId(), 0).getTotalElements());
  }

  @Test
  void reviewListDoesNotFetchBodyOrPasswordAndSupportsDetachedDisplay() {
    var sql =
        SqlCapture.capture(
            () -> {
              var row = service.reviewQueue(manager.getId(), 0).getContent().getFirst();
              entityManager.clear();
              assertEquals(pending.getId(), row.id());
              assertEquals(document.getId(), row.documentId());
              assertEquals("연구개발", row.departmentName());
              assertEquals("author", row.authorName());
              assertEquals("검토할 문서", row.title());
            });
    String listQuery =
        sql.stream()
            .filter(query -> query.contains("from document_version") && query.contains("order by"))
            .findFirst()
            .orElseThrow();
    assertFalse(listQuery.contains(".content"), listQuery);
    assertFalse(listQuery.contains(".password"), listQuery);
    service.approve(pending.getId(), manager.getId());
    var empty = service.reviewQueue(manager.getId(), Integer.MAX_VALUE);
    assertTrue(empty.isEmpty());
    assertEquals(0, empty.getNumber());
  }

  @Test
  void missingReviewTargetReturns404() {
    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class, () -> service.approve(Long.MAX_VALUE, manager.getId()));
    assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
  }

  @Test
  void requiredPositionHasDatabaseDefaultForLegacyInsertShape() {
    entityManager
        .createNativeQuery(
            "insert into document(author_id, department_id, created_at) values (:author,"
                + " :department, CURRENT_TIMESTAMP)")
        .setParameter("author", author.getId())
        .setParameter("department", department.getId())
        .executeUpdate();
    var positions =
        entityManager
            .createQuery("select d.requiredPosition from Document d", Position.class)
            .getResultList();
    assertTrue(positions.stream().allMatch(position -> position == Position.STAFF));
  }

  private User user(String nickname, Position position, Department userDepartment) {
    User user = new User(nickname, nickname + "@example.com", "hash");
    ReflectionTestUtils.setField(user, "position", position);
    ReflectionTestUtils.setField(user, "department", userDepartment);
    entityManager.persist(user);
    return user;
  }

  private long countDocuments() {
    return entityManager
        .createQuery("select count(d) from Document d", Long.class)
        .getSingleResult();
  }

  @Test
  void adminAutoApprovedStaffDocumentIsImmediatelyReadableByEligiblePeers() {
    User adminAuthor = user("admin-staff-author", Position.ADMIN, department);
    Document created =
        service.create("관리자가 등록한 공개 부서 문서", "본문", Position.STAFF, adminAuthor.getId());
    Document confidential =
        service.create("관리자가 등록한 과장 등급 문서", "본문", Position.MANAGER, adminAuthor.getId());
    entityManager.flush();
    entityManager.clear();

    DocumentVersion visible = service.documentdetail(created.getId(), peer.getId());

    assertEquals(DocumentStatus.APPROVED, visible.getStatus());
    assertEquals(adminAuthor.getId(), visible.getReviewedBy().getId());
    assertNotNull(visible.getReviewedAt());
    assertEquals(
        List.of(visible.getId()),
        service
            .accessibleDocuments(peer.getId(), 0, 20, "")
            .map(DocumentListItem::versionId)
            .getContent());
    assertTrue(
        service.reviewQueue(manager.getId(), 0).stream()
            .noneMatch(v -> v.id().equals(visible.getId())));
    assertThrows(
        AccessDeniedException.class,
        () -> service.documentdetail(confidential.getId(), peer.getId()));
    User outsider = user("outside-peer", Position.STAFF, otherDepartment);
    assertThrows(
        AccessDeniedException.class,
        () -> service.documentdetail(created.getId(), outsider.getId()));
  }

  @Test
  void adminAutoApprovalStillRequiresAnActiveAssignedAuthor() {
    long before = countDocuments();
    assertThrows(
        IllegalArgumentException.class,
        () -> service.create("부서 미배정 관리자 문서", "본문", Position.STAFF, admin.getId()));
    User inactiveAdmin = user("inactive-admin", Position.ADMIN, department);
    inactiveAdmin.changeAccountStatus(AccountStatus.BAN);
    assertThrows(
        AccessDeniedException.class,
        () -> service.create("비활성 관리자 문서", "본문", Position.STAFF, inactiveAdmin.getId()));
    assertEquals(before, countDocuments());
  }
}
