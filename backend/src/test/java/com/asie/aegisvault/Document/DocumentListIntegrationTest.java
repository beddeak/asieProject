package com.asie.aegisvault.Document;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.Department.DepartmentRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:document-list-integration;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "logging.file.name="
})
@AutoConfigureMockMvc
class DocumentListIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private DocumentService service;
    @Autowired private UserRepository users;
    @Autowired private DepartmentRepository departments;
    @Autowired private DocumentRepository documents;
    @Autowired private DocumentVersionRepository versions;
    @Autowired private DocumentActivityRepository activities;

    private Department research;
    private Department quality;
    private User writer;
    private User peer;
    private User manager;
    private User admin;
    private User outsider;
    private User executive;

    @BeforeEach
    void setUp() {
        activities.deleteAllInBatch();
        versions.deleteAllInBatch();
        documents.deleteAllInBatch();
        users.deleteAllInBatch();
        departments.deleteAllInBatch();
        research = departments.saveAndFlush(new Department("연구개발본부", "목록 테스트"));
        quality = departments.saveAndFlush(new Department("품질보증부", "다른 부서"));
        writer = account("writer", Position.STAFF, research);
        peer = account("peer", Position.STAFF, research);
        manager = account("manager", Position.MANAGER, research);
        admin = account("admin", Position.ADMIN, null);
        outsider = account("outsider", Position.MANAGER, quality);
        executive = account("executive", Position.EXECUTIVE, research);
    }

    @Test
    void anonymousListRequiresLogin() throws Exception {
        mvc.perform(get("/document/list"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/user/login"));
    }

    @Test
    void staffListUsesCurrentAccountAndHidesOtherDepartmentsGradesAndUnapprovedPeerDocuments() throws Exception {
        document(writer, Position.STAFF, "본인 초안", DocumentStatus.DRAFT);
        document(peer, Position.STAFF, "동료 승인 문서", DocumentStatus.APPROVED);
        document(peer, Position.STAFF, "숨겨진 검토 대기", DocumentStatus.PENDING_REVIEW);
        document(peer, Position.STAFF, "숨겨진 반려 문서", DocumentStatus.REJECTED);
        document(outsider, Position.STAFF, "숨겨진 타 부서", DocumentStatus.APPROVED);
        document(executive, Position.EXECUTIVE, "숨겨진 임원 문서", DocumentStatus.APPROVED);

        mvc.perform(get("/document/list").with(user("writer").roles("ADMIN"))
                        .param("viewerId", admin.getId().toString())
                        .param("departmentId", quality.getId().toString()).param("position", "ADMIN"))
                .andExpect(status().isOk()).andExpect(view().name("documentlist"))
                .andExpect(content().string(containsString("본인 초안")))
                .andExpect(content().string(containsString("동료 승인 문서")))
                .andExpect(content().string(not(containsString("숨겨진"))))
                .andExpect(content().string(containsString("2건")))
                .andExpect(content().string(not(containsString("href=\"/document/review\""))));
        assertEquals(2, service.documentList(writer.getId(), 0).documents().getTotalElements());
    }

    @Test
    void managerSeesUnapprovedDepartmentDocumentsWithinTheirGrade() throws Exception {
        document(writer, Position.STAFF, "부서 초안", DocumentStatus.DRAFT);
        document(peer, Position.STAFF, "부서 검토 대기", DocumentStatus.PENDING_REVIEW);
        document(peer, Position.STAFF, "부서 반려", DocumentStatus.REJECTED);
        document(executive, Position.EXECUTIVE, "숨겨진 임원 초안", DocumentStatus.DRAFT);
        document(outsider, Position.STAFF, "숨겨진 다른 부서", DocumentStatus.APPROVED);

        mvc.perform(get("/document/list").with(user("manager")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("부서 초안")))
                .andExpect(content().string(containsString("부서 검토 대기")))
                .andExpect(content().string(containsString("부서 반려")))
                .andExpect(content().string(not(containsString("숨겨진"))))
                .andExpect(content().string(containsString("href=\"/document/review\"")));
        assertEquals(3, service.documentList(manager.getId(), 0).documents().getTotalElements());
    }

    @Test
    void adminWithoutDepartmentSeesAllDepartmentsAndGrades() throws Exception {
        document(writer, Position.STAFF, "연구 초안", DocumentStatus.DRAFT);
        document(outsider, Position.MANAGER, "품질 문서", DocumentStatus.REJECTED);
        document(executive, Position.EXECUTIVE, "임원 문서", DocumentStatus.PENDING_REVIEW);

        mvc.perform(get("/document/list").with(user("admin").roles("STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("전체 부서")))
                .andExpect(content().string(containsString("연구 초안")))
                .andExpect(content().string(containsString("품질 문서")))
                .andExpect(content().string(containsString("임원 문서")))
                .andExpect(content().string(containsString("href=\"/admin/users\"")))
                .andExpect(content().string(not(containsString("href=\"/document/write\""))));
        assertEquals(3, service.documentList(admin.getId(), 0).documents().getTotalElements());
    }

    @Test
    void onlyHighestVersionNumberAppearsAndAnUnapprovedLatestVersionHidesOlderApproval() throws Exception {
        var original = document(writer, Position.STAFF, "오래된 승인 제목", DocumentStatus.APPROVED);
        // Persist version 3 before version 2: database insertion order must not select the latest version.
        version(original.getDocument(), 3, "최신 초안 제목", DocumentStatus.DRAFT);
        version(original.getDocument(), 2, "이전 승인 제목", DocumentStatus.APPROVED);

        var result = service.documentList(writer.getId(), 0);
        assertEquals(1, result.documents().getTotalElements());
        var row = result.documents().getContent().getFirst();
        assertEquals(3, row.versionNumber());
        assertEquals("writer", row.authorName());
        assertEquals("연구개발본부", row.departmentName());
        assertEquals("최신 초안 제목", row.title());
        assertTrue(service.documentList(peer.getId(), 0).documents().isEmpty());

        mvc.perform(get("/document/list").with(user("writer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("최신 초안 제목")))
                .andExpect(content().string(containsString("v3")))
                .andExpect(content().string(containsString("href=\"/document/detail/" + row.documentId() + "\"")))
                .andExpect(content().string(not(containsString("승인 제목"))));
    }

    @Test
    void pagingFiltersBeforeCountingAndOrdersNewestFirst() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            ids.add(document(writer, Position.STAFF, "문서 " + i, DocumentStatus.PENDING_REVIEW)
                    .getDocument().getId());
        }
        for (int i = 0; i < 21; i++) {
            document(peer, Position.STAFF, "숨겨진 문서 " + i, DocumentStatus.PENDING_REVIEW);
        }
        var first = service.documentList(writer.getId(), 0).documents();
        var second = service.documentList(writer.getId(), 1).documents();
        assertEquals(21, first.getTotalElements());
        assertEquals(20, first.getNumberOfElements());
        assertEquals(ids.reversed().subList(0, 20), first.getContent().stream().map(row -> row.documentId()).toList());
        assertEquals(1, second.getNumberOfElements());
        assertEquals(ids.getFirst(), second.getContent().getFirst().documentId());

        mvc.perform(get("/document/list").with(user("writer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("21건")))
                .andExpect(content().string(containsString("1 / 2")))
                .andExpect(content().string(containsString("href=\"/document/list?page=1\"")))
                .andExpect(content().string(not(containsString("숨겨진 문서"))));
        mvc.perform(get("/document/list").with(user("writer")).param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2 / 2")))
                .andExpect(content().string(containsString("href=\"/document/list?page=0\"")));
        assertEquals(0, service.documentList(writer.getId(), -1).documents().getNumber());
        assertEquals(1, service.documentList(writer.getId(), Integer.MAX_VALUE).documents().getNumber());
    }

    @Test
    void unassignedUserGetsGuidanceAndNewAssignmentTakesEffectOnTheNextRequest() throws Exception {
        User newcomer = account("newcomer", Position.STAFF, null);
        document(writer, Position.STAFF, "배정 후 승인 문서", DocumentStatus.APPROVED);
        mvc.perform(get("/document/list").with(user("newcomer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("소속 부서가 배정되지 않았습니다")))
                .andExpect(content().string(containsString("관리자에게 부서 배정을 요청해주세요")))
                .andExpect(content().string(not(containsString("배정 후 승인 문서"))))
                .andExpect(content().string(not(containsString("href=\"/document/write\""))));
        newcomer.assign(Position.STAFF, research);
        users.saveAndFlush(newcomer);
        mvc.perform(get("/document/list").with(user("newcomer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("배정 후 승인 문서")))
                .andExpect(content().string(containsString("href=\"/document/write\"")));
    }

    @Test
    void emptyListAndEscapedTitlesRenderWithoutDocumentBodiesOrAccountSecrets() throws Exception {
        mvc.perform(get("/document/list").with(user("writer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("열람 가능한 문서가 없습니다")))
                .andExpect(content().string(containsString("0건")));
        document(writer, Position.STAFF, "<script>alert(1)</script>", DocumentStatus.DRAFT);
        mvc.perform(get("/document/list").with(user("writer")))
                .andExpect(status().isOk()).andExpect(model().hasNoErrors())
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(not(containsString("LIST_BODY_MUST_NOT_RENDER"))))
                .andExpect(content().string(not(containsString("fixture-password-hash"))));
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"BAN", "LOCKED", "DELETED"})
    void inactiveAccountCannotListDocumentsThroughExistingAuthenticationOrService(AccountStatus accountStatus) throws Exception {
        document(writer, Position.STAFF, "계정 문서", DocumentStatus.DRAFT);
        writer.changeAccountStatus(accountStatus);
        users.saveAndFlush(writer);
        mvc.perform(get("/document/list").with(user("writer")))
                .andExpect(status().isForbidden());
        assertThrows(AccessDeniedException.class, () -> service.documentList(writer.getId(), 0));
    }

    @Test
    void writeAndAdminScreensLinkToTheListAndInvalidPageIsRejected() throws Exception {
        mvc.perform(get("/document/write").with(user("writer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/document/list\"")));
        mvc.perform(get("/admin/users").with(user("admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/document/list\"")));
        mvc.perform(get("/document/list").with(user("writer")).param("page", "invalid"))
                .andExpect(status().isBadRequest());
        assertThrows(AccessDeniedException.class, () -> service.documentList(null, 0));
        assertThrows(AccessDeniedException.class, () -> service.documentList(Long.MAX_VALUE, 0));
    }

    private User account(String nickname, Position position, Department department) {
        User account = new User(nickname, nickname + "@example.com", "fixture-password-hash");
        account.assign(position, department);
        return users.saveAndFlush(account);
    }

    private DocumentVersion document(User author, Position grade, String title, DocumentStatus status) {
        Document document = documents.saveAndFlush(new Document(author, author.getDepartment(), grade));
        return version(document, 1, title, status);
    }

    private DocumentVersion version(Document document, int number, String title, DocumentStatus status) {
        DocumentVersion version = new DocumentVersion(document, number, title, "LIST_BODY_MUST_NOT_RENDER");
        if (status != DocumentStatus.DRAFT) {
            version.submitForReview();
            if (status == DocumentStatus.APPROVED) {
                version.approve(admin);
            } else if (status == DocumentStatus.REJECTED) {
                version.reject(admin);
            }
        }
        return versions.saveAndFlush(version);
    }
}
