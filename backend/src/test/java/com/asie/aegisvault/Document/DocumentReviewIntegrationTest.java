package com.asie.aegisvault.Document;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.security.UserAccessPolicy;
import com.asie.aegisvault.support.SqlCapture;
import jakarta.persistence.EntityManager;
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

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:aegisvault-document-access-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.asie.aegisvault.support.SqlCapture",
        "logging.file.name="
})
@Import({DocumentService.class, UserAccessPolicy.class})
class DocumentReviewIntegrationTest {
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private DocumentService service;
    @Autowired
    private DocumentVersionRepository versions;

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
        assertThrows(AccessDeniedException.class,
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
        assertThrows(AccessDeniedException.class, () -> service.documentdetail(document.getId(), peer.getId()));
        assertEquals(pending.getId(), service.documentdetail(document.getId(), author.getId()).getId());
        assertEquals(pending.getId(), service.documentdetail(document.getId(), manager.getId()).getId());
        service.reject(pending.getId(), manager.getId());
        assertThrows(AccessDeniedException.class, () -> service.documentdetail(document.getId(), peer.getId()));
        assertEquals(DocumentStatus.REJECTED, service.documentdetail(document.getId(), author.getId()).getStatus());
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
        assertThrows(AccessDeniedException.class, () -> service.reviewDetail(pending.getId(), author.getId()));
        assertThrows(AccessDeniedException.class, () -> service.approve(pending.getId(), author.getId()));
        assertThrows(AccessDeniedException.class, () -> service.reject(pending.getId(), author.getId()));
    }

    @Test
    void otherDepartmentManagerCannotReadReviewOrDecide() {
        User outsider = user("outsider", Position.MANAGER, otherDepartment);
        assertTrue(service.reviewQueue(outsider.getId(), 0).isEmpty());
        assertThrows(AccessDeniedException.class, () -> service.reviewDetail(pending.getId(), outsider.getId()));
        assertThrows(AccessDeniedException.class, () -> service.approve(pending.getId(), outsider.getId()));
        assertThrows(AccessDeniedException.class, () -> service.reject(pending.getId(), outsider.getId()));
    }

    @Test
    void managerCannotApproveOrRejectOwnDocument() {
        Document own = service.create("본인 문서", "본문", Position.MANAGER, manager.getId());
        DocumentVersion version = versions.findFirstByDocumentOrderByVersionNumberDesc(own).orElseThrow();
        assertThrows(AccessDeniedException.class, () -> service.reviewDetail(version.getId(), manager.getId()));
        assertThrows(AccessDeniedException.class, () -> service.approve(version.getId(), manager.getId()));
        assertThrows(AccessDeniedException.class, () -> service.reject(version.getId(), manager.getId()));
        assertTrue(service.reviewQueue(manager.getId(), 0).stream().noneMatch(v -> v.id().equals(version.getId())));
    }

    @Test
    void adminCanReadAllDepartmentsButCannotReviewOwnDocument() {
        assertEquals(pending.getId(), service.reviewDetail(pending.getId(), admin.getId()).getId());
        assertEquals(pending.getId(), service.documentdetail(document.getId(), admin.getId()).getId());
        User adminAuthor = user("admin-author", Position.ADMIN, otherDepartment);
        Document own = service.create("관리자 문서", "본문", Position.ADMIN, adminAuthor.getId());
        DocumentVersion version = versions.findFirstByDocumentOrderByVersionNumberDesc(own).orElseThrow();
        assertTrue(service.reviewQueue(adminAuthor.getId(), 0).stream().noneMatch(v -> v.id().equals(version.getId())));
        assertThrows(AccessDeniedException.class, () -> service.reviewDetail(version.getId(), adminAuthor.getId()));
        assertThrows(AccessDeniedException.class, () -> service.approve(version.getId(), adminAuthor.getId()));
        assertThrows(AccessDeniedException.class, () -> service.reject(version.getId(), adminAuthor.getId()));
        service.approve(version.getId(), admin.getId());
        assertEquals(DocumentStatus.APPROVED, service.documentdetail(own.getId(), admin.getId()).getStatus());
    }

    @Test
    void reviewerMustMeetDocumentGradeAsWellAsManagerRank() {
        User executive = user("executive", Position.EXECUTIVE, department);
        Document confidential = service.create("임원 문서", "본문", Position.EXECUTIVE, executive.getId());
        DocumentVersion version = versions.findFirstByDocumentOrderByVersionNumberDesc(confidential).orElseThrow();
        assertThrows(AccessDeniedException.class, () -> service.documentdetail(confidential.getId(), manager.getId()));
        assertThrows(AccessDeniedException.class, () -> service.reviewDetail(version.getId(), manager.getId()));
        assertThrows(AccessDeniedException.class, () -> service.approve(version.getId(), manager.getId()));
        assertTrue(service.reviewQueue(manager.getId(), 0).stream().noneMatch(v -> v.id().equals(version.getId())));
        service.approve(version.getId(), admin.getId());
        assertThrows(AccessDeniedException.class, () -> service.documentdetail(confidential.getId(), manager.getId()));
    }

    @Test
    void repeatedDecisionIsConflictAndDoesNotOverwriteFirstReviewer() {
        service.approve(pending.getId(), manager.getId());
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.reject(pending.getId(), admin.getId()));
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
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.approve(pending.getId(), manager.getId()));
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
        var sql = SqlCapture.capture(() -> {
            var row = service.reviewQueue(manager.getId(), 0).getContent().getFirst();
            entityManager.clear();
            assertEquals(pending.getId(), row.id());
            assertEquals(document.getId(), row.documentId());
            assertEquals("연구개발", row.departmentName());
            assertEquals("author", row.authorName());
            assertEquals("검토할 문서", row.title());
        });
        String listQuery = sql.stream().filter(query -> query.contains("from document_version") && query.contains("order by"))
                .findFirst().orElseThrow();
        assertFalse(listQuery.contains(".content"), listQuery);
        assertFalse(listQuery.contains(".password"), listQuery);
        service.approve(pending.getId(), manager.getId());
        var empty = service.reviewQueue(manager.getId(), Integer.MAX_VALUE);
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.getNumber());
    }

    @Test
    void missingReviewTargetReturns404() {
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.approve(Long.MAX_VALUE, manager.getId()));
        assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
    }

    @Test
    void requiredPositionHasDatabaseDefaultForLegacyInsertShape() {
        entityManager.createNativeQuery("insert into document(author_id, department_id, created_at) values (:author, :department, CURRENT_TIMESTAMP)")
                .setParameter("author", author.getId()).setParameter("department", department.getId()).executeUpdate();
        var positions = entityManager.createQuery("select d.requiredPosition from Document d", Position.class).getResultList();
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
        return entityManager.createQuery("select count(d) from Document d", Long.class).getSingleResult();
    }
}
