package com.asie.aegisvault.Document;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.activity.DocumentActivityRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Fixtures commit before HTTP requests so open-in-view=false and detached relationship failures remain visible.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:aegisvault-document-archive-web-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "logging.file.name="
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DocumentArchiveWebIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DocumentActivityRepository activities;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            Department department = new Department("연구개발본부", "문서 담당 부서");
            entityManager.persist(department);
            User author = persistUser("archive-author", Position.STAFF, department);
            persistUser("archive-viewer", Position.STAFF, department);
            persistUser("archive-unassigned", Position.STAFF, null);
            persistUser("archive-admin", Position.ADMIN, null);
            version(author, department, "Archive report one", DocumentStatus.APPROVED);
            version(author, department, "Archive report two", DocumentStatus.APPROVED);
            version(author, department, "Unapproved report", DocumentStatus.PENDING_REVIEW);
        });
    }

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            entityManager.createQuery("delete from DocumentActivity").executeUpdate();
            entityManager.createQuery("delete from DocumentVersion").executeUpdate();
            entityManager.createQuery("delete from Document").executeUpdate();
            entityManager.createQuery("delete from User").executeUpdate();
            entityManager.createQuery("delete from Department").executeUpdate();
        });
    }

    @Test
    void archiveRendersCommittedDepartmentAndDocumentRelationshipsWithoutOpenInView() throws Exception {
        mockMvc.perform(get("/document/list").with(user("archive-viewer")))
                .andExpect(status().isOk())
                .andExpect(view().name("documentlist"))
                .andExpect(model().attribute("departmentName", "연구개발본부"))
                .andExpect(content().string(containsString("Archive report one")))
                .andExpect(content().string(containsString("Archive report two")))
                .andExpect(content().string(containsString("archive-author")))
                .andExpect(content().string(not(containsString("Unapproved report"))))
                .andExpect(content().string(containsString("2건")))
                .andExpect(content().string(containsString("href=\"/\"")));
        assertEquals(0, activities.count());
    }

    @Test
    void searchUsesQAndRetainsSearchTermAcrossPagination() throws Exception {
        mockMvc.perform(get("/document/list").with(user("archive-viewer"))
                        .param("q", " report ").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("query", "report"))
                .andExpect(content().string(containsString("name=\"q\"")))
                .andExpect(content().string(containsString("value=\"report\"")))
                .andExpect(content().string(containsString("2건")))
                .andExpect(content().string(containsString("1 / 2")))
                .andExpect(content().string(containsString("q=report")))
                .andExpect(content().string(not(containsString("Unapproved report"))));

        mockMvc.perform(get("/document/list").with(user("archive-viewer"))
                        .param("q", "<script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("검색 결과가 없습니다")))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }

    @Test
    void unassignedUserGetsAnEmptyArchiveAndAdminGetsAllDepartments() throws Exception {
        mockMvc.perform(get("/document").with(user("archive-unassigned")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("departmentName", "부서 미배정"))
                .andExpect(content().string(containsString("열람 가능한 문서가 없습니다")))
                .andExpect(content().string(containsString("0건")))
                .andExpect(content().string(not(containsString("href=\"/document/write\""))));
        mockMvc.perform(get("/document/list").with(user("archive-admin")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("departmentName", "전체 부서"))
                .andExpect(content().string(containsString("Unapproved report")))
                .andExpect(content().string(containsString("3건")));
    }

    @Test
    void anonymousArchiveRequestIsSentToLogin() throws Exception {
        mockMvc.perform(get("/document/list"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/login"));
    }

    private User persistUser(String nickname, Position position, Department department) {
        User user = new User(nickname, nickname + "@example.com", "test-hash");
        user.assign(position, department);
        entityManager.persist(user);
        return user;
    }

    private void version(User author, Department department, String title, DocumentStatus status) {
        Document document = new Document(author, department, Position.STAFF);
        entityManager.persist(document);
        DocumentVersion version = new DocumentVersion(document, 1, title, "테스트 문서 본문");
        ReflectionTestUtils.setField(version, "status", status);
        entityManager.persist(version);
    }
}
