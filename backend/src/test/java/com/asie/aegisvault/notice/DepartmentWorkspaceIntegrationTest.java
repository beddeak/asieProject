package com.asie.aegisvault.notice;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.Department.DepartmentRepository;
import com.asie.aegisvault.Department.DepartmentService;
import com.asie.aegisvault.Document.DocumentRepository;
import com.asie.aegisvault.Document.DocumentVersionRepository;
import com.asie.aegisvault.User.AccountStatus;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.UserRepository;
import com.asie.aegisvault.activity.DocumentActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:department-workspace-integration;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "logging.file.name="
})
@AutoConfigureMockMvc
class DepartmentWorkspaceIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private DepartmentNoticeService service;
    @Autowired private DepartmentNoticeRepository notices;
    @Autowired private DepartmentService departmentService;
    @Autowired private DepartmentRepository departments;
    @Autowired private UserRepository users;
    @Autowired private DocumentRepository documents;
    @Autowired private DocumentVersionRepository versions;
    @Autowired private DocumentActivityRepository activities;
    @Autowired private PasswordEncoder encoder;

    private Department research;
    private Department quality;
    private User member;
    private User manager;
    private User outsider;

    @BeforeEach
    void setUp() {
        notices.deleteAllInBatch();
        activities.deleteAllInBatch();
        versions.deleteAllInBatch();
        documents.deleteAllInBatch();
        users.deleteAllInBatch();
        departments.deleteAllInBatch();
        research = departments.saveAndFlush(new Department("연구개발본부", "연구 설계와 기술 검증"));
        quality = departments.saveAndFlush(new Department("품질보증부", "품질 검증"));
        account("admin", Position.ADMIN, null);
        manager = account("manager", Position.MANAGER, research);
        member = account("member", Position.STAFF, research);
        outsider = account("outsider", Position.EXECUTIVE, quality);
    }

    @Test
    void anonymousWorkspaceAndNoticeRequestsRequireLogin() throws Exception {
        for (String path : List.of("/", "/departments", board(research), board(research) + "/notices/new", board(research) + "/notices/1")) {
            mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/user/login"));
        }
    }

    @ParameterizedTest
    @EnumSource(value = Position.class, names = {"STAFF", "ADMIN"})
    void loginOpensHomeAndDepartmentEntryHasAReturnLink(Position position) throws Exception {
        User loginUser = new User("home-login", "home-login@example.com", encoder.encode("Home!pass123"));
        loginUser.assign(position, research);
        users.saveAndFlush(loginUser);
        var login = mvc.perform(post("/user/login").with(csrf())
                        .param("username", "home-login").param("password", "Home!pass123"))
                .andExpect(redirectedUrl("/")).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);
        mvc.perform(get("/").session(session))
                .andExpect(status().isOk()).andExpect(view().name("home"))
                .andExpect(content().string(containsString("home-login")))
                .andExpect(content().string(containsString("부서 탭·공지")))
                .andExpect(content().string(containsString("href=\"/departments\"")))
                .andExpect(content().string(containsString("action=\"/logout\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString(loginUser.getPassword()))));
        mvc.perform(get("/departments").session(session))
                .andExpect(status().isOk()).andExpect(view().name("departmentworkspace"))
                .andExpect(content().string(containsString("연구개발본부")))
                .andExpect(content().string(containsString("href=\"/\">홈</a>")));
    }

    @Test
    void homeMenusUseCurrentDatabasePositionAndAssignment() throws Exception {
        mvc.perform(get("/").with(user("member").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("href=\"/document/write\"")))
                .andExpect(content().string(not(containsString("href=\"/document/review\""))))
                .andExpect(content().string(not(containsString("href=\"/admin/users\""))))
                .andExpect(content().string(not(containsString("href=\"/admin/activity\""))));
        mvc.perform(get("/").with(user("manager").roles("STAFF")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("href=\"/document/review\"")))
                .andExpect(content().string(not(containsString("href=\"/admin/users\""))));
        mvc.perform(get("/").with(user("admin").roles("STAFF")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("href=\"/admin/users\"")))
                .andExpect(content().string(containsString("href=\"/admin/activity\"")))
                .andExpect(content().string(containsString("href=\"/document/review\"")))
                .andExpect(content().string(not(containsString("href=\"/document/write\""))));
        account("unassigned-manager", Position.MANAGER, null);
        mvc.perform(get("/").with(user("unassigned-manager")))
                .andExpect(status().isOk()).andExpect(view().name("home"))
                .andExpect(content().string(containsString("href=\"/departments\"")))
                .andExpect(content().string(containsString("소속 부서 배정이 필요합니다")))
                .andExpect(content().string(not(containsString("href=\"/document/write\""))))
                .andExpect(content().string(not(containsString("href=\"/document/review\""))));
        manager.assign(Position.STAFF, research);
        users.saveAndFlush(manager);
        mvc.perform(get("/").with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("href=\"/document/review\""))));
    }

    @Test
    void existingAndNewDepartmentsAutomaticallyBecomeAdminTabsThroughBothCreationPaths() throws Exception {
        mvc.perform(get("/departments").with(user("admin").roles("STAFF")))
                .andExpect(status().isOk()).andExpect(view().name("departmentworkspace"))
                .andExpect(content().string(containsString("연구개발본부")))
                .andExpect(content().string(containsString("품질보증부")));
        var result = mvc.perform(post("/admin/departments").with(user("admin")).with(csrf())
                        .param("name", "정보보안실").param("description", "보안 검토"))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("createdDepartmentId")).andReturn();
        Long newId = (Long) result.getFlashMap().get("createdDepartmentId");
        mvc.perform(get("/departments/" + newId).with(user("admin")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("정보보안실")))
                .andExpect(content().string(containsString("등록된 공지가 없습니다")))
                .andExpect(content().string(containsString("aria-current=\"page\"")))
                .andExpect(content().string(containsString("/departments/" + newId + "/notices/new")));
        mvc.perform(post("/dep/create").with(user("admin")).with(csrf()).param("name", "사업관리부"))
                .andExpect(status().isOk());
        var tabs = service.workspace("admin", null, 0).tabs();
        assertEquals(4, tabs.size());
        assertEquals(4, tabs.stream().map(DepartmentWorkspace.Tab::id).distinct().count());
        assertTrue(tabs.stream().anyMatch(tab -> tab.name().equals("사업관리부")));
        // Reading repeatedly must not create additional tab records or departments.
        assertEquals(tabs, service.workspace("admin", null, 0).tabs());
        assertEquals(4, departments.count());
    }

    @Test
    void memberOnlySeesTheirDepartmentAndNewAssignmentChangesTheTabImmediately() throws Exception {
        mvc.perform(get("/departments").with(user("member")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("연구개발본부")))
                .andExpect(content().string(not(containsString("품질보증부"))))
                .andExpect(content().string(not(containsString("공지 작성"))));
        member.assign(Position.STAFF, quality);
        users.saveAndFlush(member);
        mvc.perform(get("/departments").with(user("member")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("품질보증부")))
                .andExpect(content().string(not(containsString("연구개발본부"))));
        mvc.perform(get(board(research)).with(user("member"))).andExpect(status().isForbidden());
    }

    @Test
    void unassignedMemberAndAdminWithNoDepartmentsGetUsefulEmptyStates() throws Exception {
        account("unassigned", Position.STAFF, null);
        mvc.perform(get("/departments").with(user("unassigned")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("소속 부서가 배정되지 않았습니다")))
                .andExpect(content().string(not(containsString("연구개발본부"))));
        mvc.perform(get(board(research)).with(user("unassigned"))).andExpect(status().isForbidden());
        users.deleteAllInBatch();
        departments.deleteAllInBatch();
        account("admin", Position.ADMIN, null);
        mvc.perform(get("/departments").with(user("admin")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("등록된 부서가 없습니다")))
                .andExpect(content().string(containsString("부서를 생성하면 이곳에 해당 부서 탭이 자동으로 표시됩니다")));
        var first = departmentService.create("새로운 부서", "", "admin");
        assertEquals(first.getId(), service.workspace("admin", null, 0).selected().id());
    }

    @Test
    void managerCreatesNoticeWithAuthenticatedAuthorAndMembersCanReadEscapedContent() throws Exception {
        mvc.perform(get(board(research) + "/notices/new").with(user("manager")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")));
        var created = mvc.perform(post(board(research) + "/notices").with(user("manager")).with(csrf())
                        .param("title", "<script>공지</script>").param("content", "첫 줄\n<script>alert(1)</script>")
                        .param("author", "admin").param("departmentId", quality.getId().toString()))
                .andExpect(status().is3xxRedirection()).andReturn();
        String location = created.getResponse().getRedirectedUrl();
        var saved = notices.findAll().getFirst();
        assertEquals(manager.getId(), saved.getAuthor().getId());
        assertEquals(research.getId(), saved.getDepartment().getId());
        mvc.perform(get(location).with(user("member")))
                .andExpect(status().isOk()).andExpect(view().name("departmentnoticedetail"))
                .andExpect(content().string(containsString("&lt;script&gt;공지&lt;/script&gt;")))
                .andExpect(content().string(containsString("첫 줄\n&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(containsString("manager")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(not(containsString("fixture-password-hash"))))
                .andExpect(content().string(not(containsString("공지 수정"))))
                .andExpect(content().string(not(containsString("삭제 확인"))));
        assertTrue(service.workspace("admin", quality.getId(), 0).notices().isEmpty());
    }

    @Test
    void memberCannotManageEvenWithForgedAdminAuthority() throws Exception {
        Long id = create("manager", research, "공지");
        for (String path : List.of(board(research) + "/notices/new", notice(research, id) + "/edit")) {
            mvc.perform(get(path).with(user("member").roles("ADMIN"))).andExpect(status().isForbidden());
        }
        mvc.perform(post(board(research) + "/notices").with(user("member").roles("ADMIN")).with(csrf())
                        .param("title", "위조 공지").param("content", "본문"))
                .andExpect(status().isForbidden());
        mvc.perform(post(notice(research, id) + "/edit").with(user("member").roles("ADMIN")).with(csrf())
                        .param("title", "위조 수정").param("content", "본문").param("version", "0"))
                .andExpect(status().isForbidden());
        mvc.perform(post(notice(research, id) + "/delete").with(user("member").roles("ADMIN")).with(csrf()).param("version", "0"))
                .andExpect(status().isForbidden());
        assertThrows(AccessDeniedException.class, () -> service.create("member", research.getId(), form("직접 호출", "본문", null)));
        assertEquals("공지", service.detail("member", research.getId(), id).title());
    }

    @Test
    void otherDepartmentCannotReadCreateUpdateOrDeleteAndMismatchedNoticeIdsReturn404() throws Exception {
        Long researchId = create("manager", research, "연구 공지");
        Long qualityId = create("outsider", quality, "품질 공지");
        for (String path : List.of(board(research), notice(research, researchId), board(research) + "/notices/new")) {
            mvc.perform(get(path).with(user("outsider"))).andExpect(status().isForbidden());
        }
        mvc.perform(post(board(research) + "/notices").with(user("outsider")).with(csrf())
                        .param("title", "다른 부서 공지").param("content", "본문"))
                .andExpect(status().isForbidden());
        mvc.perform(post(notice(research, researchId) + "/edit").with(user("outsider")).with(csrf())
                        .param("title", "변경").param("content", "본문").param("version", "0"))
                .andExpect(status().isForbidden());
        mvc.perform(post(notice(research, researchId) + "/delete").with(user("outsider")).with(csrf()).param("version", "0"))
                .andExpect(status().isForbidden());
        mvc.perform(get(notice(research, qualityId)).with(user("manager"))).andExpect(status().isNotFound());
        mvc.perform(post(notice(research, qualityId) + "/edit").with(user("manager")).with(csrf())
                        .param("title", "").param("content", ""))
                .andExpect(status().isNotFound());
        mvc.perform(get("/departments/" + Long.MAX_VALUE).with(user("admin"))).andExpect(status().isNotFound());
        assertEquals(2, notices.count());
    }

    @Test
    void adminCanManageAnyDepartmentAndManagersCanEditAndDeleteOwnDepartmentNotices() throws Exception {
        Long id = create("admin", research, "관리자 공지");
        mvc.perform(get(notice(research, id) + "/edit").with(user("manager")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("관리자 공지")))
                .andExpect(content().string(containsString("name=\"version\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(post(notice(research, id) + "/edit").with(user("manager")).with(csrf())
                        .param("title", "수정 공지").param("content", "수정된 본문").param("version", "0"))
                .andExpect(redirectedUrl(notice(research, id)));
        var detail = service.detail("member", research.getId(), id);
        assertEquals("수정 공지", detail.title());
        assertEquals("수정된 본문", detail.content());
        assertEquals("admin", detail.authorName());
        assertEquals(1L, detail.version());
        mvc.perform(get(notice(research, id)).with(user("manager")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("공지 수정")));
        mvc.perform(post(notice(research, id) + "/delete").with(user("manager")).with(csrf()).param("version", "1"))
                .andExpect(redirectedUrl(board(research)));
        assertFalse(notices.existsById(id));
        Long other = create("admin", quality, "전체 부서 관리");
        assertTrue(service.detail("admin", quality.getId(), other).canManage());
        service.delete("admin", quality.getId(), other, 0L);
        assertFalse(notices.existsById(other));
    }

    @Test
    void oldEditAndDeleteFormsAreRejectedWithoutOverwritingNewContent() throws Exception {
        Long id = create("manager", research, "원본");
        service.update("manager", research.getId(), id, form("최신 수정", "새 본문", 0L));
        mvc.perform(post(notice(research, id) + "/edit").with(user("manager")).with(csrf())
                        .param("title", "오래된 수정").param("content", "이전 본문").param("version", "0"))
                .andExpect(status().isConflict());
        mvc.perform(post(notice(research, id) + "/delete").with(user("manager")).with(csrf()).param("version", "0"))
                .andExpect(status().isConflict());
        assertEquals("최신 수정", service.detail("member", research.getId(), id).title());
        assertEquals(1, notices.count());
    }

    @Test
    void mutationsRequireCsrf() throws Exception {
        Long id = create("manager", research, "원본");
        for (String path : List.of(board(research) + "/notices", notice(research, id) + "/edit", notice(research, id) + "/delete")) {
            mvc.perform(post(path).with(user("manager"))
                            .param("title", "수정").param("content", "본문").param("version", "0"))
                    .andExpect(status().isForbidden());
        }
        assertEquals("원본", service.detail("member", research.getId(), id).title());
    }

    @Test
    void invalidInputsReturnFormsAndEnforceTitleAndContentLimits() throws Exception {
        mvc.perform(post(board(research) + "/notices").with(user("manager")).with(csrf())
                        .param("title", " ").param("content", ""))
                .andExpect(status().isOk()).andExpect(view().name("departmentnoticeform"))
                .andExpect(content().string(containsString("공지 제목을 입력해주세요")))
                .andExpect(content().string(containsString("공지 내용을 입력해주세요")));
        mvc.perform(post(board(research) + "/notices").with(user("manager")).with(csrf())
                        .param("title", "a".repeat(256)).param("content", "b".repeat(10001)))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("noticeForm", "title", "content"));
        assertEquals(0, notices.count());
        Long id = service.create("manager", research.getId(), form("a".repeat(255), "b".repeat(10000), null));
        assertEquals(10000, service.detail("member", research.getId(), id).content().length());
        mvc.perform(post(notice(research, id) + "/edit").with(user("manager")).with(csrf())
                        .param("title", "<script>공지</script>").param("content", "").param("version", "0"))
                .andExpect(status().isOk()).andExpect(view().name("departmentnoticeform"))
                .andExpect(content().string(containsString("&lt;script&gt;공지&lt;/script&gt;")));
        assertEquals("a".repeat(255), service.detail("member", research.getId(), id).title());
    }

    @Test
    void departmentNoticePagesHaveCorrectCountsStableOrderAndNoOtherDepartmentContent() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            ids.add(create("manager", research, "연구 공지 " + i));
        }
        create("outsider", quality, "다른 부서 공지");
        var first = service.workspace("member", research.getId(), 0).notices();
        var last = service.workspace("member", research.getId(), 1).notices();
        assertEquals(21, first.getTotalElements());
        assertEquals(ids.reversed().subList(0, 20), first.getContent().stream().map(NoticeSummary::id).toList());
        assertEquals(1, last.getNumberOfElements());
        assertEquals(ids.getFirst(), last.getContent().getFirst().id());
        mvc.perform(get(board(research)).with(user("member")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("21건")))
                .andExpect(content().string(containsString("page=1")))
                .andExpect(content().string(not(containsString("다른 부서 공지"))))
                .andExpect(content().string(not(containsString("공지 본문은 목록에 표시하지 않습니다"))));
        assertEquals(0, service.workspace("member", research.getId(), -1).notices().getNumber());
        assertEquals(1, service.workspace("member", research.getId(), Integer.MAX_VALUE).notices().getNumber());
    }

    @Test
    void demotionImmediatelyRemovesNoticeManagementFromAnExistingSession() throws Exception {
        Long id = create("manager", research, "원본");
        manager.assign(Position.STAFF, research);
        users.saveAndFlush(manager);
        mvc.perform(get(notice(research, id)).with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("공지 수정"))));
        mvc.perform(post(notice(research, id) + "/delete").with(user("manager").roles("MANAGER")).with(csrf()).param("version", "0"))
                .andExpect(status().isForbidden());
        assertTrue(notices.existsById(id));
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"BAN", "LOCKED", "DELETED"})
    void inactiveUsersCannotReadOrManageNoticesThroughControllerOrService(AccountStatus status) throws Exception {
        Long id = create("manager", research, "원본");
        manager.changeAccountStatus(status);
        users.saveAndFlush(manager);
        mvc.perform(get("/").with(user("manager"))).andExpect(status().isForbidden());
        mvc.perform(get(board(research)).with(user("manager"))).andExpect(status().isForbidden());
        mvc.perform(post(notice(research, id) + "/delete").with(user("manager")).with(csrf()).param("version", "0"))
                .andExpect(status().isForbidden());
        assertThrows(AccessDeniedException.class, () -> service.workspace("manager", research.getId(), 0));
        assertThrows(AccessDeniedException.class, () -> service.delete("manager", research.getId(), id, 0L));
    }

    @Test
    void existingMenusLinkToDepartmentsAndTheStylesheetIsPublic() throws Exception {
        mvc.perform(get("/document/write").with(user("member")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("href=\"/departments\"")));
        mvc.perform(get("/admin/users").with(user("admin")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("href=\"/departments\"")));
        mvc.perform(get("/css/departmentworkspace.css")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
        mvc.perform(get("/css/home.css")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
        mvc.perform(get("/departments").with(user("member")).param("page", "invalid"))
                .andExpect(status().isBadRequest());
        assertThrows(AccessDeniedException.class, () -> service.workspace(null, null, 0));
        assertThrows(ResponseStatusException.class, () -> service.detail("admin", research.getId(), Long.MAX_VALUE));
    }

    private User account(String name, Position position, Department department) {
        User account = new User(name, name + "@example.com", "fixture-password-hash");
        account.assign(position, department);
        return users.saveAndFlush(account);
    }

    private Long create(String actor, Department department, String title) {
        return service.create(actor, department.getId(), form(title, "공지 본문은 목록에 표시하지 않습니다", null));
    }

    private NoticeForm form(String title, String content, Long version) {
        NoticeForm form = new NoticeForm();
        form.setTitle(title);
        form.setContent(content);
        form.setVersion(version);
        return form;
    }

    private String board(Department department) {
        return "/departments/" + department.getId();
    }

    private String notice(Department department, Long id) {
        return board(department) + "/notices/" + id;
    }
}
