package com.asie.aegisvault.admin;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.Department.DepartmentRepository;
import com.asie.aegisvault.Department.DepartmentService;
import com.asie.aegisvault.Document.Document;
import com.asie.aegisvault.Document.DocumentRepository;
import com.asie.aegisvault.Document.DocumentService;
import com.asie.aegisvault.Document.DocumentStatus;
import com.asie.aegisvault.Document.DocumentVersionRepository;
import com.asie.aegisvault.User.AccountStatus;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.UserRepository;
import com.asie.aegisvault.activity.DocumentAction;
import com.asie.aegisvault.activity.DocumentActivity;
import com.asie.aegisvault.activity.DocumentActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-integration;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "logging.file.name="
})
@AutoConfigureMockMvc
class AdminUserIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private DepartmentRepository departments;
    @Autowired private DocumentRepository documents;
    @Autowired private DocumentVersionRepository versions;
    @Autowired private DocumentActivityRepository activities;
    @Autowired private AdminUserService adminService;
    @Autowired private DepartmentService departmentService;
    @Autowired private DocumentService documentService;
    @Autowired private PasswordEncoder encoder;
    @Autowired private PlatformTransactionManager transactionManager;

    private User admin;
    private User secondAdmin;
    private User writer;
    private Department research;
    private Department quality;
    private String passwordHash;

    @BeforeEach
    void setUp() {
        activities.deleteAllInBatch();
        versions.deleteAllInBatch();
        documents.deleteAllInBatch();
        users.deleteAllInBatch();
        departments.deleteAllInBatch();
        passwordHash = encoder.encode("Demo!pass123");
        research = departments.saveAndFlush(new Department("연구개발본부", "문서 작성"));
        quality = departments.saveAndFlush(new Department("품질보증부", "품질시험"));
        admin = account("admin", Position.ADMIN, null);
        secondAdmin = account("second-admin", Position.ADMIN, research);
        writer = account("writer", Position.STAFF, research);
    }

    @Test
    void anonymousAdminPageRequiresLoginAndPublicStylesAreAvailable() throws Exception {
        mvc.perform(get("/admin/users")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/css/admin.css")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
        mvc.perform(get("/js/admin.js")).andExpect(status().isOk());
    }

    @Test
    void adminPageRendersDetachedDepartmentsFormsAndCsrfWithoutPasswordHashes() throws Exception {
        mvc.perform(get("/admin/users").with(user("admin").roles("STAFF")))
                .andExpect(status().isOk()).andExpect(view().name("adminusers"))
                .andExpect(content().string(containsString("연구개발본부")))
                .andExpect(content().string(containsString("writer@example.com")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("action=\"/admin/departments\"")))
                .andExpect(content().string(not(containsString(passwordHash))));
    }

    @Test
    void homeRendersDetachedProfileAndAccessibleDocumentsForStaffAndAdmin() throws Exception {
        Document document = documentService.create("홈에서 열람할 문서", "문서 본문", Position.STAFF, writer.getId());
        mvc.perform(get("/").with(user("writer").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(view().name("home"))
                .andExpect(model().attribute("isAdmin", false))
                .andExpect(model().attribute("documentCount", 1L))
                .andExpect(content().string(containsString("연구개발본부")))
                .andExpect(content().string(containsString("writer")))
                .andExpect(content().string(containsString("홈에서 열람할 문서")))
                .andExpect(content().string(containsString("href=\"/document/detail/" + document.getId() + "\"")))
                .andExpect(content().string(not(containsString("href=\"/admin/users\""))))
                .andExpect(content().string(not(containsString(passwordHash))));
        mvc.perform(get("/").with(user("admin").roles("STAFF")))
                .andExpect(status().isOk()).andExpect(view().name("home"))
                .andExpect(model().attribute("isAdmin", true))
                .andExpect(model().attribute("canReview", true))
                .andExpect(content().string(containsString("href=\"/admin/users\"")));
    }

    @Test
    void forgedAdminAuthorityCannotOpenPagesPostActionsOrCallServices() throws Exception {
        for (String path : List.of("/admin/users", "/admin/activity", "/dep/create")) {
            mvc.perform(get(path).with(user("writer").roles("ADMIN")))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/admin/users/" + writer.getId() + "/status")
                        .with(user("writer").roles("ADMIN")).with(csrf()).param("status", "BAN"))
                .andExpect(status().isForbidden());
        assertThrows(AccessDeniedException.class, () -> adminService.delete("writer", admin.getId()));
        assertThrows(AccessDeniedException.class,
                () -> departmentService.create("금지된 부서", "", "writer"));
    }

    @Test
    void mutatingRequestsRequireCsrf() throws Exception {
        for (String suffix : List.of("assignment", "status", "delete")) {
            mvc.perform(post("/admin/users/" + writer.getId() + "/" + suffix)
                            .with(user("admin").roles("ADMIN")))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/admin/departments").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        assertEquals(AccountStatus.ACTIVE, reload(writer).getAccountStatus());
    }

    @Test
    void assignmentUsesAuthenticatedAdminAndIgnoresForgedEntityFields() throws Exception {
        mvc.perform(post("/admin/users/" + writer.getId() + "/assignment")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("position", "MANAGER").param("departmentId", quality.getId().toString())
                        .param("actor", "writer").param("password", "injected").param("accountStatus", "DELETED"))
                .andExpect(redirectedUrl("/admin/users"));
        User changed = reload(writer);
        assertEquals(Position.MANAGER, changed.getPosition());
        assertEquals(quality.getId(), changed.getDepartment().getId());
        assertEquals(AccountStatus.ACTIVE, changed.getAccountStatus());
        assertEquals(passwordHash, changed.getPassword());
    }

    @Test
    void blankDepartmentUnassignsAndAdminAppointmentWorks() throws Exception {
        mvc.perform(post("/admin/users/" + writer.getId() + "/assignment")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("position", "ADMIN").param("departmentId", ""))
                .andExpect(redirectedUrl("/admin/users"));
        assertEquals(Position.ADMIN, reload(writer).getPosition());
        assertNull(reload(writer).getDepartment());
        mvc.perform(get("/admin/users").with(user("writer").roles("STAFF")))
                .andExpect(status().isOk());
    }

    @Test
    void invalidAssignmentAndNonexistentDepartmentLeaveUserUnchanged() throws Exception {
        for (String position : List.of("", "UNKNOWN")) {
            mvc.perform(post("/admin/users/" + writer.getId() + "/assignment")
                            .with(user("admin").roles("ADMIN")).with(csrf()).param("position", position))
                    .andExpect(redirectedUrl("/admin/users"))
                    .andExpect(flash().attributeExists("errorMessage"));
        }
        mvc.perform(post("/admin/users/" + writer.getId() + "/assignment")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("position", "MANAGER").param("departmentId", "999999"))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(Position.STAFF, reload(writer).getPosition());
        assertEquals(research.getId(), reload(writer).getDepartment().getId());
    }

    @Test
    void searchEscapesWildcardCharactersAndUsesDatabasePagination() {
        account("literal%_user", Position.STAFF, quality);
        for (int index = 0; index < 22; index++) {
            account("employee-" + index, Position.STAFF, quality);
        }
        assertEquals(1, adminService.search("admin", "%_", null, null, null, 0).getTotalElements());
        var first = adminService.search("admin", "EMPLOYEE-", quality.getId(), Position.STAFF, AccountStatus.ACTIVE, 0);
        var second = adminService.search("admin", "EMPLOYEE-", quality.getId(), Position.STAFF, AccountStatus.ACTIVE, 1);
        assertEquals(22, first.getTotalElements());
        assertEquals(20, first.getNumberOfElements());
        assertEquals(2, second.getNumberOfElements());
        assertEquals(1, adminService.search("admin", "", 0L, null, null, -1).getTotalElements());
    }

    @Test
    void selfDemotionSuspensionAndDeletionAreBlocked() throws Exception {
        mvc.perform(post("/admin/users/" + admin.getId() + "/assignment")
                        .with(user("admin").roles("ADMIN")).with(csrf()).param("position", "STAFF"))
                .andExpect(flash().attributeExists("errorMessage"));
        for (AccountStatus state : List.of(AccountStatus.BAN, AccountStatus.LOCKED)) {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.changeStatus("admin", admin.getId(), state));
        }
        assertThrows(IllegalArgumentException.class, () -> adminService.delete("admin", admin.getId()));
        assertEquals(Position.ADMIN, reload(admin).getPosition());
        assertEquals(AccountStatus.ACTIVE, reload(admin).getAccountStatus());
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"BAN", "LOCKED", "DELETED"})
    void inactiveAccountsCannotLoginOrUseExistingSessionsOrDocumentServices(AccountStatus state) throws Exception {
        User inactive = reload(writer);
        inactive.changeAccountStatus(state);
        users.saveAndFlush(inactive);
        mvc.perform(post("/user/login").with(csrf())
                        .param("username", "writer").param("password", "Demo!pass123"))
                .andExpect(redirectedUrl("/user/login?error"));
        mvc.perform(get("/document/write").with(user("writer")))
                .andExpect(status().isForbidden());
        assertThrows(AccessDeniedException.class, () -> documentService.assignablePositions(writer.getId()));
        assertThrows(AccessDeniedException.class,
                () -> documentService.create("새 문서", "본문", Position.STAFF, writer.getId()));
        assertThrows(AccessDeniedException.class,
                () -> documentService.documentdetail(999L, writer.getId()));
        assertEquals(0, activities.count());
    }

    @Test
    void banningAnAlreadyLoggedInUserInvalidatesTheirSessionAndRestoringAllowsLogin() throws Exception {
        var login = mvc.perform(post("/user/login").with(csrf())
                        .param("username", "writer").param("password", "Demo!pass123"))
                .andExpect(redirectedUrl("/")).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);
        adminService.changeStatus("admin", writer.getId(), AccountStatus.BAN);
        mvc.perform(get("/document/write").session(session)).andExpect(status().isForbidden());
        assertTrue(session.isInvalid());
        adminService.changeStatus("admin", writer.getId(), AccountStatus.ACTIVE);
        mvc.perform(post("/user/login").with(csrf())
                        .param("username", "writer").param("password", "Demo!pass123"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void staleAdminSessionLosesAccessAfterDemotion() throws Exception {
        adminService.assign("admin", secondAdmin.getId(), new UserAssignmentRequest(Position.STAFF, research.getId()));
        mvc.perform(get("/admin/users").with(user("second-admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        assertThrows(AccessDeniedException.class, () -> adminService.search("second-admin", "", null, null, null, 0));
    }

    @Test
    void deletedAccountRetainsDocumentsAndActivitiesAndCannotBeRestored() throws Exception {
        Document document = documentService.create("보존할 문서", "원본 본문", Position.STAFF, writer.getId());
        long activityCount = activities.count();
        mvc.perform(post("/admin/users/" + writer.getId() + "/delete")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/users"));
        assertEquals(AccountStatus.DELETED, reload(writer).getAccountStatus());
        assertTrue(documents.existsById(document.getId()));
        assertEquals(activityCount, activities.count());
        assertThrows(IllegalArgumentException.class,
                () -> adminService.changeStatus("admin", writer.getId(), AccountStatus.ACTIVE));
        assertThrows(IllegalArgumentException.class,
                () -> adminService.assign("admin", writer.getId(), new UserAssignmentRequest(Position.MANAGER, null)));
        assertEquals(1, adminService.activity("admin", writer.getId(), null, null, "", 0).getTotalElements());
        mvc.perform(get("/admin/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("삭제")));
    }

    @Test
    void summaryCountsDeletedAndUnassignedAccountsConsistently() {
        adminService.changeStatus("admin", writer.getId(), AccountStatus.BAN);
        var summary = adminService.summary("admin");
        assertEquals(new AdminUserService.Summary(3, 2, 1, 1), summary);
        adminService.delete("admin", writer.getId());
        assertEquals(new AdminUserService.Summary(3, 2, 0, 1), adminService.summary("admin"));
    }

    @Test
    void existingDepartmentFragmentCreatesDepartmentAndPreservesInvalidInput() throws Exception {
        mvc.perform(post("/admin/departments").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", " 정보보안실 ").param("description", ""))
                .andExpect(redirectedUrl("/admin/users"));
        assertTrue(departments.existsByName("정보보안실"));
        mvc.perform(post("/admin/departments").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", "정보보안실").param("description", "<script>alert(1)</script>"))
                .andExpect(flash().attributeExists("errorMessage"))
                .andExpect(flash().attribute("openDepartmentForm", true));
        mvc.perform(post("/admin/departments").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", " "))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void standaloneDepartmentPageRemainsAvailableOnlyToAdmin() throws Exception {
        mvc.perform(get("/dep/create").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("action=\"/dep/create\"")));
        mvc.perform(post("/dep/create").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", "사업관리부"))
                .andExpect(status().isOk());
        assertTrue(departments.existsByName("사업관리부"));
    }

    @Test
    void allImplementedDocumentActionsAreRecordedAndFilterable() throws Exception {
        Document document = documentService.create("활동 기록 시험", "본문", Position.STAFF, writer.getId());
        Long versionId = versions.findFirstByDocumentOrderByVersionNumberDesc(document).orElseThrow().getId();
        mvc.perform(get("/document/detail/" + document.getId()).with(user("writer"))).andExpect(status().isOk());
        mvc.perform(get("/document/review/" + versionId).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(post("/document/review/" + versionId + "/approve")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/document/review"));
        Document rejected = documentService.create("반려 시험", "본문", Position.STAFF, writer.getId());
        Long rejectedId = versions.findFirstByDocumentOrderByVersionNumberDesc(rejected).orElseThrow().getId();
        documentService.reject(rejectedId, secondAdmin.getId());
        assertEquals(List.of(DocumentAction.CREATED, DocumentAction.READ, DocumentAction.REVIEW_OPENED,
                        DocumentAction.APPROVED, DocumentAction.CREATED, DocumentAction.REJECTED),
                activities.findAll(Sort.by("id")).stream().map(DocumentActivity::getAction).toList());
        assertEquals(1, adminService.activity("admin", writer.getId(), document.getId(), DocumentAction.READ,
                "시험", 0).getTotalElements());
        assertEquals(0, adminService.activity("admin", writer.getId(), rejected.getId(), DocumentAction.READ,
                "", 0).getTotalElements());
        mvc.perform(get("/admin/activity").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(view().name("adminactivity"))
                .andExpect(content().string(containsString("활동 기록 시험")))
                .andExpect(content().string(containsString("문서 열람")));
    }

    @Test
    void documentActivityRollsBackWithTheDocumentAndKeepsOriginalRankSnapshot() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            documentService.create("롤백 문서", "본문", Position.STAFF, writer.getId());
            transaction.setRollbackOnly();
        });
        assertEquals(0, documents.count());
        assertEquals(0, activities.count());
        documentService.create("직급 기록", "본문", Position.STAFF, writer.getId());
        adminService.assign("admin", writer.getId(), new UserAssignmentRequest(Position.MANAGER, quality.getId()));
        assertEquals(Position.STAFF, activities.findAll().getFirst().getActorPosition());
    }

    @Test
    void evenAdminCannotApproveTheirOwnDocument() {
        Document document = documentService.create("관리자 작성", "본문", Position.STAFF, secondAdmin.getId());
        Long versionId = versions.findFirstByDocumentOrderByVersionNumberDesc(document).orElseThrow().getId();
        assertThrows(AccessDeniedException.class, () -> documentService.approve(versionId, secondAdmin.getId()));
        assertEquals(1, activities.count());
        assertEquals(DocumentStatus.PENDING_REVIEW, versions.findById(versionId).orElseThrow().getStatus());
    }

    @Test
    void escapedUserAndDocumentTitlesRenderWithoutExecutingHtml() throws Exception {
        account("<script>user</script>", Position.STAFF, null);
        documentService.create("<script>alert(1)</script>", "본문", Position.STAFF, writer.getId());
        mvc.perform(get("/admin/users").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(containsString("&lt;script&gt;user&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>user</script>"))));
        mvc.perform(get("/admin/activity").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }

    @Test
    void concurrentAdministratorsCannotSuspendEachOtherAndLeaveNoActiveAdmin() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> suspend(ready, start, "admin", secondAdmin.getId()));
            var second = executor.submit(() -> suspend(ready, start, "second-admin", admin.getId()));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS));
            assertEquals(1, users.findByPosition(Position.ADMIN).stream()
                    .filter(account -> account.getAccountStatus() == AccountStatus.ACTIVE).count());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private int suspend(CountDownLatch ready, CountDownLatch start, String actor, Long target) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 작업 시작 시간이 초과되었습니다.");
        }
        try {
            adminService.changeStatus(actor, target, AccountStatus.BAN);
            return 1;
        } catch (AccessDeniedException denied) {
            return 0;
        }
    }

    private User account(String nickname, Position position, Department department) {
        User account = new User(nickname, nickname + "@example.com", passwordHash);
        account.assign(position, department);
        return users.saveAndFlush(account);
    }

    private User reload(User account) {
        return users.findById(account.getId()).orElseThrow();
    }
}
