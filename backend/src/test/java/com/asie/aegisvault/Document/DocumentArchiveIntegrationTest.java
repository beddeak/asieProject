package com.asie.aegisvault.Document;

import static org.junit.jupiter.api.Assertions.*;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.Document.dto.DocumentListItem;
import com.asie.aegisvault.User.AccountStatus;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.activity.DocumentActivityRepository;
import com.asie.aegisvault.security.UserAccessPolicy;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.transaction.TestTransaction;

// The archive uses an isolated in-memory database; production documents are never accessed.
@DataJpaTest(
    showSql = false,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:aegisvault-document-archive-test;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.open-in-view=false",
      "logging.file.name="
    })
@Import({
  DocumentService.class,
  UserAccessPolicy.class,
  com.asie.aegisvault.security.DocumentAccess.class,
  DocumentLocks.class,
  com.asie.aegisvault.project.ProjectAccess.class
})
class DocumentArchiveIntegrationTest {
  @Autowired private EntityManager entityManager;
  @Autowired private DocumentService service;
  @Autowired private DocumentActivityRepository activities;

  private Department department;
  private Department otherDepartment;
  private User author;
  private User peer;
  private User manager;
  private User admin;

  @BeforeEach
  void setUp() {
    department = new Department("연구개발", "기술 문서");
    otherDepartment = new Department("인사", "다른 부서");
    entityManager.persist(department);
    entityManager.persist(otherDepartment);
    author = user("author", Position.STAFF, department);
    peer = user("peer", Position.STAFF, department);
    manager = user("manager", Position.MANAGER, department);
    admin = user("admin", Position.ADMIN, null);
  }

  @Test
  void peerSeesOnlyApprovedDocumentsInTheirDepartmentAndGrade() {
    DocumentVersion allowed =
        version(author, department, Position.STAFF, "열람 가능", DocumentStatus.APPROVED);
    version(author, department, Position.STAFF, "검토 대기", DocumentStatus.PENDING_REVIEW);
    version(author, department, Position.STAFF, "반려", DocumentStatus.REJECTED);
    version(author, department, Position.MANAGER, "상위 직급", DocumentStatus.APPROVED);
    version(author, otherDepartment, Position.STAFF, "타 부서", DocumentStatus.APPROVED);
    version(author, department, Position.ADMIN, "관리자 등급", DocumentStatus.APPROVED);
    flushAndClear();

    var page = service.accessibleDocuments(peer.getId(), 0, 20, "");

    assertEquals(List.of(allowed.getId()), page.map(DocumentListItem::versionId).getContent());
    assertEquals(1, page.getTotalElements());
  }

  @Test
  void authorsCanBrowseOwnUnapprovedDocumentsWithoutBypassingDepartmentOrGrade() {
    DocumentVersion pending =
        version(author, department, Position.STAFF, "내 대기 문서", DocumentStatus.PENDING_REVIEW);
    DocumentVersion rejected =
        version(author, department, Position.STAFF, "내 반려 문서", DocumentStatus.REJECTED);
    version(author, department, Position.MANAGER, "내 상위 등급 문서", DocumentStatus.DRAFT);
    version(author, otherDepartment, Position.STAFF, "내 타 부서 문서", DocumentStatus.DRAFT);
    flushAndClear();

    var page = service.accessibleDocuments(author.getId(), 0, 20, "");

    assertEquals(2, page.getTotalElements());
    assertEquals(
        List.of(rejected.getId(), pending.getId()),
        page.map(DocumentListItem::versionId).getContent());
  }

  @Test
  void managersSeeUnapprovedDocumentsOnlyWithinTheirDepartmentAndGrade() {
    for (DocumentStatus status : DocumentStatus.values()) {
      version(author, department, Position.MANAGER, status.name(), status);
    }
    version(author, department, Position.EXECUTIVE, "임원 문서", DocumentStatus.PENDING_REVIEW);
    version(author, otherDepartment, Position.STAFF, "타 부서 문서", DocumentStatus.PENDING_REVIEW);
    flushAndClear();

    assertEquals(
        DocumentStatus.values().length,
        service.accessibleDocuments(manager.getId(), 0, 20, "").getTotalElements());
  }

  @Test
  void adminWithoutDepartmentSeesEveryDepartmentAndGradeAndStatus() {
    DocumentVersion confidential =
        version(author, otherDepartment, Position.ADMIN, "관리자 기록", DocumentStatus.DRAFT);
    DocumentVersion pending =
        version(author, department, Position.STAFF, "승인 전 기록", DocumentStatus.PENDING_REVIEW);
    flushAndClear();

    var page = service.accessibleDocuments(admin.getId(), 0, 20, "");

    assertEquals(
        List.of(pending.getId(), confidential.getId()),
        page.map(DocumentListItem::versionId).getContent());
  }

  @Test
  void activeUserWithoutDepartmentGetsEmptyPageAndInactiveUsersAreDenied() {
    User unassigned = user("unassigned", Position.STAFF, null);
    version(author, department, Position.STAFF, "승인 문서", DocumentStatus.APPROVED);
    flushAndClear();

    var empty = service.accessibleDocuments(unassigned.getId(), 0, 6, "");
    assertTrue(empty.isEmpty());
    assertEquals(6, empty.getSize());
    User attached = entityManager.find(User.class, unassigned.getId());
    attached.changeAccountStatus(AccountStatus.LOCKED);
    assertThrows(
        AccessDeniedException.class,
        () -> service.accessibleDocuments(unassigned.getId(), 0, 6, ""));
    assertThrows(AccessDeniedException.class, () -> service.accessibleDocuments(null, 0, 6, ""));
    assertThrows(
        AccessDeniedException.class, () -> service.accessibleDocuments(Long.MAX_VALUE, 0, 6, ""));
  }

  @Test
  void olderApprovedVersionCannotLeakWhenLatestVersionIsUnapproved() {
    DocumentVersion old =
        version(author, department, Position.STAFF, "이전 승인 제목", DocumentStatus.APPROVED);
    DocumentVersion latest = new DocumentVersion(old.getDocument(), 2, "최신 검토 제목", "최신 본문");
    latest.submitForReview();
    entityManager.persist(latest);
    flushAndClear();

    assertTrue(service.accessibleDocuments(peer.getId(), 0, 20, "").isEmpty());
    assertEquals(
        List.of(latest.getId()),
        service
            .accessibleDocuments(author.getId(), 0, 20, "")
            .map(DocumentListItem::versionId)
            .getContent());
    assertTrue(service.accessibleDocuments(admin.getId(), 0, 20, "이전 승인").isEmpty());
    assertEquals(1, service.accessibleDocuments(admin.getId(), 0, 20, "최신 검토").getTotalElements());
  }

  @Test
  void searchIsCaseInsensitiveAndTreatsWildcardCharactersLiterally() {
    DocumentVersion allowed =
        version(author, department, Position.STAFF, "Alpha 100%_\\ 계획", DocumentStatus.APPROVED);
    version(author, department, Position.STAFF, "ALPHA 100 일반 계획", DocumentStatus.APPROVED);
    version(
        author, otherDepartment, Position.STAFF, "Alpha 100%_\\ 외부 계획", DocumentStatus.APPROVED);
    flushAndClear();

    assertEquals(
        2, service.accessibleDocuments(peer.getId(), 0, 20, "  aLpHa  ").getTotalElements());
    assertEquals(
        List.of(allowed.getId()),
        service
            .accessibleDocuments(peer.getId(), 0, 20, "%_\\")
            .map(DocumentListItem::versionId)
            .getContent());
    assertEquals(2, service.accessibleDocuments(peer.getId(), 0, 20, null).getTotalElements());
    assertTrue(service.accessibleDocuments(peer.getId(), 0, 20, "미등록 제목").isEmpty());
  }

  @Test
  void paginationUsesNewestVersionTimestampThenIdAndBoundsPageSize() {
    DocumentVersion first =
        version(author, department, Position.STAFF, "첫 문서", DocumentStatus.APPROVED);
    DocumentVersion second =
        version(author, department, Position.STAFF, "둘째 문서", DocumentStatus.APPROVED);
    DocumentVersion third =
        version(author, department, Position.STAFF, "셋째 문서", DocumentStatus.APPROVED);
    entityManager.flush();
    LocalDateTime newest = LocalDateTime.of(2026, 10, 7, 12, 0);
    setVersionTime(first, newest);
    setVersionTime(second, newest.minusDays(1));
    setVersionTime(third, newest);
    entityManager.clear();

    var page = service.accessibleDocuments(peer.getId(), 0, 2, "");
    assertEquals(
        List.of(third.getId(), first.getId()), page.map(DocumentListItem::versionId).getContent());
    assertEquals(3, page.getTotalElements());
    assertEquals(2, page.getTotalPages());
    assertEquals(
        List.of(second.getId()),
        service
            .accessibleDocuments(peer.getId(), 1, 2, "")
            .map(DocumentListItem::versionId)
            .getContent());
    assertEquals(0, service.accessibleDocuments(peer.getId(), -5, 0, "").getNumber());
    assertEquals(1, service.accessibleDocuments(peer.getId(), -5, 0, "").getSize());
    assertEquals(50, service.accessibleDocuments(peer.getId(), 0, 500, "").getSize());
  }

  @Test
  void archiveLoadsDisplayRelationshipsAndDoesNotCreateReadActivity() {
    version(author, department, Position.STAFF, "조회 기록", DocumentStatus.APPROVED);
    flushAndClear();
    long activityCount = activities.count();

    DocumentListItem result =
        service.accessibleDocuments(peer.getId(), 0, 6, "").getContent().getFirst();

    assertEquals(activityCount, activities.count());
    TestTransaction.end();
    assertEquals("author", result.authorName());
    assertEquals("연구개발", result.departmentName());
  }

  private User user(String nickname, Position position, Department assignedDepartment) {
    User user = new User(nickname, nickname + "@example.com", "test-hash");
    user.assign(position, assignedDepartment);
    entityManager.persist(user);
    return user;
  }

  private DocumentVersion version(
      User author, Department department, Position grade, String title, DocumentStatus status) {
    Document document = new Document(author, department, grade);
    entityManager.persist(document);
    DocumentVersion version = new DocumentVersion(document, 1, title, "문서 본문");
    org.springframework.test.util.ReflectionTestUtils.setField(version, "status", status);
    entityManager.persist(version);
    return version;
  }

  private void setVersionTime(DocumentVersion version, LocalDateTime time) {
    entityManager
        .createQuery("update DocumentVersion v set v.createdAt = :time where v.id = :id")
        .setParameter("time", time)
        .setParameter("id", version.getId())
        .executeUpdate();
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }
}
