package com.asie.aegisvault.Document;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.security.UserAccessPolicy;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 실제 파일 DB와 분리된 메모리 DB만 사용하며 각 테스트의 데이터는 롤백합니다.
@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:aegisvault-document-access-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "logging.file.name="
})
@Import({DocumentService.class, UserAccessPolicy.class})
class DocumentReadIntegrationTest {
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DocumentService service;

    private Department department;
    private User author;
    private Document document;

    @BeforeEach
    void setUp() {
        department = new Department("연구개발본부", "통합 테스트 부서");
        entityManager.persist(department);
        author = persistUser("author", Position.STAFF, department);
        document = new Document(author, department);
        entityManager.persist(document);
        // 저장된 순서가 아닌 versionNumber 기준으로 최신 버전을 골라야 합니다.
        entityManager.persist(new DocumentVersion(document, 3, "최신 제목", "최신 본문"));
        entityManager.persist(new DocumentVersion(document, 1, "첫 제목", "첫 본문"));
        entityManager.persist(new DocumentVersion(document, 2, "이전 제목", "이전 본문"));
    }

    @Test
    void latestVersionAndMetadataRemainReadableAfterTransactionEnds() {
        User viewer = persistUser("manager", Position.MANAGER, department);
        Document otherDocument = new Document(author, department);
        entityManager.persist(otherDocument);
        entityManager.persist(new DocumentVersion(otherDocument, 99, "다른 문서", "다른 본문"));
        flushAndClear();

        DocumentVersion detail = service.documentdetail(document.getId(), viewer.getId());

        assertEquals(document.getId(), detail.getDocument().getId());
        assertEquals(3, detail.getVersionNumber());
        assertTrue(Hibernate.isInitialized(detail.getDocument().getAuthor()));
        assertTrue(Hibernate.isInitialized(detail.getDocument().getDepartment()));
        TestTransaction.end();

        // open-in-view=false인 상세 화면에서도 추가 DB 조회 없이 읽을 수 있어야 합니다.
        assertEquals("author", detail.getDocument().getAuthor().getNickname());
        assertEquals("연구개발본부", detail.getDocument().getDepartment().getName());
        assertEquals("최신 제목", detail.getTitle());
        assertEquals("최신 본문", detail.getContent());
    }

    @Test
    void executiveInAnotherDepartmentCannotRead() {
        Department otherDepartment = new Department("인사본부", "다른 부서");
        entityManager.persist(otherDepartment);
        User viewer = persistUser("executive", Position.EXECUTIVE, otherDepartment);
        flushAndClear();

        assertThrows(AccessDeniedException.class,
                () -> service.documentdetail(document.getId(), viewer.getId()));
    }

    @Test
    void sameDepartmentStaffCanReadTheirOwnDraft() {
        flushAndClear();

        assertEquals(3, service.documentdetail(document.getId(), author.getId()).getVersionNumber());
    }

    @Test
    void adminWithoutDepartmentCanRead() {
        User admin = persistUser("admin", Position.ADMIN, null);
        flushAndClear();

        DocumentVersion detail = service.documentdetail(document.getId(), admin.getId());

        assertEquals(3, detail.getVersionNumber());
        assertEquals("최신 본문", detail.getContent());
    }

    private User persistUser(String nickname, Position position, Department userDepartment) {
        User user = new User(nickname, nickname + "@example.com", "test-password-hash");
        ReflectionTestUtils.setField(user, "position", position);
        ReflectionTestUtils.setField(user, "department", userDepartment);
        entityManager.persist(user);
        return user;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
