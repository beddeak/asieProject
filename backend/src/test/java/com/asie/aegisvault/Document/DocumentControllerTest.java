package com.asie.aegisvault.Document;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.Document.dto.ReviewQueueItem;
import com.asie.aegisvault.User.AccountStatus;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.UserRepository;
import com.asie.aegisvault.config.SecurityConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

// 실제 컨트롤러·템플릿·SecurityConfig를 사용하되, 서비스와 Repository는 mock으로 DB 접근을 막습니다.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentControllerTest {
  private AnnotationConfigWebApplicationContext context;
  private MockMvc mockMvc;
  private DocumentService documentService;
  private UserRepository userRepository;

  @BeforeAll
  void setUpContext() {
    context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(PageTestConfig.class);
    context.refresh();
    documentService = context.getBean(DocumentService.class);
    userRepository = context.getBean(UserRepository.class);
    mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
  }

  @BeforeEach
  void resetMocks() {
    reset(documentService, userRepository);
    mockAuthor();
    when(documentService.actions(
            org.mockito.ArgumentMatchers.any(User.class),
            org.mockito.ArgumentMatchers.any(DocumentVersion.class)))
        .thenReturn(new DocumentService.Actions(false, false, false, true, false));
    when(documentService.comments(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(org.springframework.data.domain.Page.empty());
  }

  @AfterAll
  void closeContext() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  void anonymousUserIsSentToLogin() throws Exception {
    mockMvc
        .perform(get("/document/write"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/user/login"));
    verifyNoInteractions(documentService, userRepository);
  }

  @Test
  void emptyFormRendersAllowedGradesAndCsrfToken() throws Exception {
    mockMvc
        .perform(get("/document/write").with(user("writer")))
        .andExpect(status().isOk())
        .andExpect(view().name("documentwrite"))
        .andExpect(model().hasNoErrors())
        .andExpect(content().string(containsString("action=\"/document/write\"")))
        .andExpect(content().string(containsString("name=\"title\"")))
        .andExpect(content().string(containsString("name=\"content\"")))
        .andExpect(content().string(containsString("name=\"requiredPosition\"")))
        .andExpect(content().string(containsString("value=\"MANAGER\"")))
        .andExpect(content().string(not(containsString("value=\"EXECUTIVE\""))))
        .andExpect(content().string(containsString("name=\"_csrf\"")))
        .andExpect(content().string(containsString("/css/documentwrite.css")))
        .andExpect(content().string(not(containsString("name=\"authorId\""))));
    verify(documentService).assignablePositions(7L);
    verifyNoMoreInteractions(documentService);
  }

  @Test
  void stylesheetIsServedToLoggedInUser() throws Exception {
    mockMvc
        .perform(get("/css/documentwrite.css").with(user("writer")))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/css"))
        .andExpect(content().string(containsString(".document-write-page")));
  }

  @Test
  void validFormUsesAuthenticatedAuthorAndShowsSuccess() throws Exception {
    mockAuthor();
    Document saved = mock(Document.class);
    when(saved.getId()).thenReturn(42L);
    when(documentService.create("시험 보고서", "시험 결과입니다.", Position.STAFF, 7L)).thenReturn(saved);
    mockMvc
        .perform(
            write("시험 보고서", "시험 결과입니다.")
                .param("authorId", "999")
                .param("versionNumber", "99")
                .param("status", "APPROVED"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/document/detail/42"))
        .andExpect(flash().attribute("successMessage", "문서가 등록되어 검토 대기로 전환되었습니다."));
    verify(userRepository, times(2)).findByNickname("writer");
    verify(documentService).create("시험 보고서", "시험 결과입니다.", Position.STAFF, 7L);
  }

  @ParameterizedTest
  @CsvSource({"title,''", "title,'   '", "content,''", "content,'   '"})
  void blankFieldsRenderErrorsWithoutSaving(String field, String value) throws Exception {
    String title = field.equals("title") ? value : "유지할 제목";
    String contentValue = field.equals("content") ? value : "유지할 본문";
    mockMvc
        .perform(write(title, contentValue))
        .andExpect(status().isOk())
        .andExpect(model().attributeHasFieldErrors("documentCreateRequest", field))
        .andExpect(content().string(containsString("입력 내용을 확인해주세요.")))
        .andExpect(content().string(containsString(field.equals("title") ? "유지할 본문" : "유지할 제목")));
    verify(documentService).assignablePositions(7L);
    verifyNoMoreInteractions(documentService);
  }

  @Test
  void titleOver50CharactersIsRejected() throws Exception {
    mockMvc
        .perform(write("가".repeat(51), "본문"))
        .andExpect(status().isOk())
        .andExpect(model().attributeHasFieldErrors("documentCreateRequest", "title"))
        .andExpect(content().string(containsString("문서 제목은 50자 이하여야 합니다")));
    verify(documentService).assignablePositions(7L);
    verifyNoMoreInteractions(documentService);
  }

  @Test
  void missingMetadataReturnsVisibleValidationErrors() throws Exception {
    mockMvc
        .perform(
            post("/document/write")
                .with(user("writer"))
                .with(csrf())
                .param("title", "제목")
                .param("content", "본문"))
        .andExpect(status().isOk())
        .andExpect(model().attributeHasFieldErrors("documentCreateRequest", "requiredPosition"))
        .andExpect(content().string(containsString("열람 가능한 최소 직급을 선택해주세요")));
    verify(documentService).assignablePositions(7L);
    verifyNoMoreInteractions(documentService);
  }

  @Test
  void invalidMetadataTypesReturnFormInsteadOfServerError() throws Exception {
    mockMvc
        .perform(
            post("/document/write")
                .with(user("writer"))
                .with(csrf())
                .param("title", "제목")
                .param("content", "본문")
                .param("requiredPosition", "UNKNOWN"))
        .andExpect(status().isOk())
        .andExpect(model().attributeHasFieldErrors("documentCreateRequest", "requiredPosition"))
        .andExpect(view().name("documentwrite"));
    verify(documentService).assignablePositions(7L);
    verifyNoMoreInteractions(documentService);
  }

  @Test
  void serviceErrorPreservesAndEscapesInput() throws Exception {
    mockAuthor();
    String title = "<b>제목</b>";
    String contentValue = "</textarea><script>alert(1)</script>";
    when(documentService.create(title, contentValue, Position.STAFF, 7L))
        .thenThrow(new IllegalArgumentException("소속 부서가 없습니다"));
    mockMvc
        .perform(write(title, contentValue))
        .andExpect(status().isOk())
        .andExpect(view().name("documentwrite"))
        .andExpect(content().string(containsString("소속 부서가 없습니다")))
        .andExpect(content().string(containsString("&lt;b&gt;제목&lt;/b&gt;")))
        .andExpect(
            content()
                .string(containsString("&lt;/textarea&gt;&lt;script&gt;alert(1)&lt;/script&gt;")))
        .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
  }

  @Test
  void postWithoutCsrfTokenIsBlocked() throws Exception {
    mockMvc
        .perform(
            post("/document/write")
                .with(user("writer"))
                .param("title", "제목")
                .param("content", "본문")
                .param("versionNumber", "1")
                .param("status", "DRAFT"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(documentService, userRepository);
  }

  @Test
  void anonymousDetailRequestIsSentToLogin() throws Exception {
    mockMvc
        .perform(get("/document/detail/42"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/user/login"));
    verifyNoInteractions(documentService, userRepository);
  }

  @Test
  void detailUsesAuthenticatedViewerAndRendersAuthorizedVersion() throws Exception {
    mockAuthor();
    User author = new User("original-author", "author@example.com", "test-password-hash");
    Department department = new Department("연구개발본부", "기술 문서");
    Document document = new Document(author, department);
    DocumentVersion version = new DocumentVersion(document, 3, "최신 시험 보고서", "승인된 열람 요청의 본문");
    when(documentService.documentdetail(42L, 7L)).thenReturn(version);

    mockMvc
        .perform(
            get("/document/detail/42")
                .with(user("writer"))
                .param("viewerId", "999")
                .param("position", "ADMIN"))
        .andExpect(status().isOk())
        .andExpect(view().name("documentdetail"))
        .andExpect(model().attribute("documentVersion", version))
        .andExpect(content().string(containsString("최신 시험 보고서")))
        .andExpect(content().string(containsString("승인된 열람 요청의 본문")))
        .andExpect(content().string(containsString("original-author")))
        .andExpect(content().string(containsString("연구개발본부")))
        .andExpect(content().string(containsString("href=\"/document/list\"")))
        .andExpect(content().string(containsString("문서 목록으로 돌아가기")));

    verify(userRepository, times(2)).findByNickname("writer");
    verify(documentService).documentdetail(42L, 7L);
  }

  @Test
  void forbiddenDetailRequestReturns403() throws Exception {
    mockAuthor();
    when(documentService.documentdetail(42L, 7L))
        .thenThrow(new AccessDeniedException("열람 권한이 없습니다."));

    mockMvc
        .perform(get("/document/detail/42").with(user("writer")))
        .andExpect(status().isForbidden());
  }

  @Test
  void deletedViewerCannotReadUsingAnExistingLoginSession() throws Exception {
    when(userRepository.findByNickname("writer")).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/document/detail/42").with(user("writer")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(documentService);
  }

  @Test
  void missingDocumentReturns404() throws Exception {
    mockAuthor();
    when(documentService.documentdetail(42L, 7L))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));

    mockMvc
        .perform(get("/document/detail/42").with(user("writer")))
        .andExpect(status().isNotFound());
  }

  private void mockAuthor() {
    User author = mock(User.class);
    when(author.getId()).thenReturn(7L);
    when(author.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
    when(author.getPosition()).thenReturn(Position.STAFF);
    when(userRepository.findByNickname("writer")).thenReturn(Optional.of(author));
    when(documentService.assignablePositions(7L))
        .thenReturn(List.of(Position.STAFF, Position.ASSISTANT_MANAGER, Position.MANAGER));
  }

  private MockHttpServletRequestBuilder write(String title, String contentValue) {
    return post("/document/write")
        .with(user("writer"))
        .with(csrf())
        .param("title", title)
        .param("content", contentValue)
        .param("requiredPosition", "STAFF");
  }

  @Test
  void reviewQueueRendersAuthorizedDocumentsAndEmptyState() throws Exception {
    var version =
        new ReviewQueueItem(
            99L,
            42L,
            "검토 문서",
            1,
            "연구개발본부",
            "original-author",
            Position.STAFF,
            java.time.LocalDateTime.now());
    when(documentService.reviewQueue(7L, 0)).thenReturn(new PageImpl<>(List.of(version)));
    mockMvc
        .perform(get("/document/review").with(user("writer")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("검토 문서")))
        .andExpect(content().string(containsString("/document/review/99")));
    when(documentService.reviewQueue(7L, 0)).thenReturn(new PageImpl<>(List.of()));
    mockMvc
        .perform(get("/document/review").with(user("writer")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("검토할 문서가 없습니다.")));
  }

  @Test
  void reviewDetailRendersPostActionsWithCsrf() throws Exception {
    when(documentService.reviewDetail(99L, 7L)).thenReturn(reviewVersion());
    mockMvc
        .perform(get("/document/review/99").with(user("writer")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("href=\"/document/review\"")))
        .andExpect(content().string(containsString("검토함으로 돌아가기")))
        .andExpect(content().string(containsString("action=\"/document/review/99/approve\"")))
        .andExpect(content().string(containsString("action=\"/document/review/99/reject\"")))
        .andExpect(content().string(containsString("name=\"_csrf\"")));
  }

  @ParameterizedTest
  @CsvSource({"approve", "reject"})
  void reviewPostUsesAuthenticatedReviewerAndRequiresCsrf(String action) throws Exception {
    mockMvc
        .perform(post("/document/review/99/" + action).with(user("writer")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(documentService);

    mockMvc
        .perform(
            post("/document/review/99/" + action)
                .with(user("writer"))
                .with(csrf())
                .param("reviewerId", "999")
                .param("reason", "시험 근거 보완 필요"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/document/review"));
    if (action.equals("approve")) {
      verify(documentService).approve(99L, 7L);
    } else {
      verify(documentService).reject(99L, 7L, "시험 근거 보완 필요");
    }
  }

  @Test
  void reviewFailuresKeepForbiddenAndConflictStatus() throws Exception {
    when(documentService.reviewQueue(7L, 0)).thenThrow(new AccessDeniedException("검토 권한이 없습니다."));
    mockMvc.perform(get("/document/review").with(user("writer"))).andExpect(status().isForbidden());
    doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리되었습니다."))
        .when(documentService)
        .approve(99L, 7L);
    mockMvc
        .perform(post("/document/review/99/approve").with(user("writer")).with(csrf()))
        .andExpect(status().isConflict());
  }

  private DocumentVersion reviewVersion() {
    User author = new User("review-author", "review@example.com", "hash");
    Document document = new Document(author, new Department("연구개발", "테스트"));
    DocumentVersion version = new DocumentVersion(document, 1, "검토 문서", "검토할 본문");
    ReflectionTestUtils.setField(version, "id", 99L);
    ReflectionTestUtils.setField(
        version, "createdAt", java.time.LocalDateTime.of(2026, 9, 30, 12, 0));
    version.submitForReview();
    return version;
  }

  @ParameterizedTest
  @CsvSource({"/document/review", "/document/review/99"})
  void anonymousReviewRequestsAreRedirectedToLogin(String path) throws Exception {
    mockMvc
        .perform(get(path))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/user/login"));
    verifyNoInteractions(documentService, userRepository);
  }

  @Test
  void forgedGradeGets403InsteadOfBypassingSelectOptions() throws Exception {
    when(documentService.create("제목", "본문", Position.ADMIN, 7L))
        .thenThrow(new AccessDeniedException("직급을 초과했습니다."));
    mockMvc
        .perform(
            post("/document/write")
                .with(user("writer"))
                .with(csrf())
                .param("title", "제목")
                .param("content", "본문")
                .param("requiredPosition", "ADMIN"))
        .andExpect(status().isForbidden());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  @EnableWebSecurity
  @Import(SecurityConfig.class)
  static class PageTestConfig implements WebMvcConfigurer {
    @Bean
    DocumentService documentService() {
      return mock(DocumentService.class);
    }

    @Bean
    UserRepository userRepository() {
      return mock(UserRepository.class);
    }

    @Bean
    UserDetailsService userDetailsService() {
      return mock(UserDetailsService.class);
    }

    @Bean
    DocumentController documentController(DocumentService service, UserRepository repository) {
      return new DocumentController(service, repository, documentPage(service));
    }

    @Bean
    com.asie.aegisvault.audit.SecurityAudit securityAudit() {
      return mock(com.asie.aegisvault.audit.SecurityAudit.class);
    }

    @Bean
    DocumentPage documentPage(DocumentService service) {
      var attachments = mock(com.asie.aegisvault.attachment.AttachmentRepository.class);
      when(attachments.findByVersionId(
              org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
          .thenReturn(org.springframework.data.domain.Page.empty());
      var notes = mock(ReviewNoteRepository.class);
      when(notes.findByVersionId(
              org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
          .thenReturn(org.springframework.data.domain.Page.empty());
      var actors = mock(com.asie.aegisvault.security.CurrentUser.class);
      when(actors.get(org.mockito.ArgumentMatchers.any())).thenReturn(mock(User.class));
      return new DocumentPage(
          service,
          attachments,
          notes,
          actors,
          mock(com.asie.aegisvault.security.DocumentAccess.class));
    }

    @Bean
    com.asie.aegisvault.web.DisplayTime displayTime() {
      return new com.asie.aegisvault.web.DisplayTime("UTC");
    }

    @Bean
    SpringTemplateEngine templateEngine() {
      ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
      resolver.setPrefix("templates/");
      resolver.setSuffix(".html");
      resolver.setTemplateMode("HTML");
      resolver.setCharacterEncoding("UTF-8");
      SpringTemplateEngine engine = new SpringTemplateEngine();
      engine.addDialect(new org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect());
      engine.setTemplateResolver(resolver);
      return engine;
    }

    @Bean
    ThymeleafViewResolver viewResolver(SpringTemplateEngine engine) {
      ThymeleafViewResolver resolver = new ThymeleafViewResolver();
      resolver.setTemplateEngine(engine);
      resolver.setCharacterEncoding("UTF-8");
      return resolver;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
      registry.addResourceHandler("/css/**").addResourceLocations("classpath:/static/css/");
    }
  }

  @Test
  void adminFormShowsImmediateApprovalAndKeepsCsrf() throws Exception {
    mockAdmin();
    mockMvc
        .perform(get("/document/write").with(user("admin").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(view().name("documentwrite"))
        .andExpect(model().attribute("automaticallyApproved", true))
        .andExpect(content().string(containsString("관리자가 작성한 문서는 등록 즉시 승인됩니다.")))
        .andExpect(content().string(containsString("문서 등록")))
        .andExpect(content().string(containsString("승인 완료")))
        .andExpect(content().string(containsString("APPROVED")))
        .andExpect(content().string(not(containsString("PENDING_REVIEW"))))
        .andExpect(content().string(not(containsString("등록 및 검토 요청"))))
        .andExpect(content().string(containsString("name=\"_csrf\"")));
    verify(documentService).assignablePositions(11L);
    verifyNoMoreInteractions(documentService);
  }

  @Test
  void adminPostUsesCurrentAuthorAndShowsApprovedDetailAfterRedirect() throws Exception {
    User admin = mockAdmin();
    Document saved = new Document(admin, admin.getDepartment(), Position.STAFF);
    ReflectionTestUtils.setField(saved, "id", 42L);
    DocumentVersion approvedVersion = new DocumentVersion(saved, 1, "관리자 기록", "확정된 기록입니다.");
    ReflectionTestUtils.setField(approvedVersion, "status", DocumentStatus.APPROVED);
    when(documentService.create("관리자 기록", "확정된 기록입니다.", Position.STAFF, 11L)).thenReturn(saved);
    when(documentService.documentdetail(42L, 11L)).thenReturn(approvedVersion);

    MvcResult result =
        mockMvc
            .perform(
                post("/document/write")
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf())
                    .param("title", "관리자 기록")
                    .param("content", "확정된 기록입니다.")
                    .param("requiredPosition", "STAFF")
                    .param("authorId", "999")
                    .param("status", "PENDING_REVIEW")
                    .param("automaticallyApproved", "false"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/document/detail/42"))
            .andExpect(flash().attribute("successMessage", "관리자 문서가 등록되어 승인 완료되었습니다."))
            .andReturn();

    mockMvc
        .perform(
            get("/document/detail/42")
                .with(user("admin").roles("ADMIN"))
                .flashAttrs(result.getFlashMap())
                .param("viewerId", "999"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("documentVersion", approvedVersion))
        .andExpect(content().string(containsString("관리자 문서가 등록되어 승인 완료되었습니다.")))
        .andExpect(content().string(containsString("확정된 기록입니다.")))
        .andExpect(content().string(containsString("data-status=\"APPROVED\"")))
        .andExpect(content().string(containsString("승인 완료")));
    verify(documentService).create("관리자 기록", "확정된 기록입니다.", Position.STAFF, 11L);
    verify(documentService).documentdetail(42L, 11L);
  }

  @Test
  void adminValidationFailureKeepsAutomaticApprovalForm() throws Exception {
    mockAdmin();
    mockMvc
        .perform(
            post("/document/write")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .param("title", " ")
                .param("content", "입력한 본문")
                .param("requiredPosition", "STAFF"))
        .andExpect(status().isOk())
        .andExpect(view().name("documentwrite"))
        .andExpect(model().attributeHasFieldErrors("documentCreateRequest", "title"))
        .andExpect(model().attribute("automaticallyApproved", true))
        .andExpect(content().string(containsString("입력한 본문")))
        .andExpect(content().string(containsString("관리자가 작성한 문서는 등록 즉시 승인됩니다.")))
        .andExpect(content().string(not(containsString("PENDING_REVIEW"))));
    verify(documentService).assignablePositions(11L);
    verifyNoMoreInteractions(documentService);
  }

  @Test
  void adminServiceFailureKeepsAutomaticApprovalForm() throws Exception {
    mockAdmin();
    when(documentService.create("관리자 기록", "유지할 본문", Position.STAFF, 11L))
        .thenThrow(new IllegalArgumentException("문서를 등록할 수 없습니다."));
    mockMvc
        .perform(
            post("/document/write")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .param("title", "관리자 기록")
                .param("content", "유지할 본문")
                .param("requiredPosition", "STAFF"))
        .andExpect(status().isOk())
        .andExpect(view().name("documentwrite"))
        .andExpect(model().attribute("automaticallyApproved", true))
        .andExpect(content().string(containsString("문서를 등록할 수 없습니다.")))
        .andExpect(content().string(containsString("유지할 본문")))
        .andExpect(content().string(containsString("관리자가 작성한 문서는 등록 즉시 승인됩니다.")))
        .andExpect(content().string(not(containsString("등록 및 검토 요청"))));
  }

  @Test
  void staleAdminAuthorityCannotChangeCurrentNonadminApprovalPath() throws Exception {
    Document saved = mock(Document.class);
    when(saved.getId()).thenReturn(42L);
    when(documentService.create("일반 기록", "검토할 본문", Position.STAFF, 7L)).thenReturn(saved);
    mockMvc
        .perform(get("/document/write").with(user("writer").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(model().attribute("automaticallyApproved", false))
        .andExpect(content().string(containsString("등록 및 검토 요청")))
        .andExpect(content().string(not(containsString("관리자가 작성한 문서는 등록 즉시 승인됩니다."))));
    mockMvc
        .perform(
            post("/document/write")
                .with(user("writer").roles("ADMIN"))
                .with(csrf())
                .param("title", "일반 기록")
                .param("content", "검토할 본문")
                .param("requiredPosition", "STAFF")
                .param("automaticallyApproved", "true")
                .param("status", "APPROVED"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/document/detail/42"))
        .andExpect(flash().attribute("successMessage", "문서가 등록되어 검토 대기로 전환되었습니다."));
    verify(documentService).create("일반 기록", "검토할 본문", Position.STAFF, 7L);
  }

  private User mockAdmin() {
    Department department = new Department("보안관리부", "관리자 소속 부서");
    ReflectionTestUtils.setField(department, "id", 3L);
    User admin = new User("admin", "admin@example.com", "test-password-hash");
    ReflectionTestUtils.setField(admin, "id", 11L);
    admin.assign(Position.ADMIN, department);
    when(userRepository.findByNickname("admin")).thenReturn(Optional.of(admin));
    when(documentService.assignablePositions(11L)).thenReturn(List.of(Position.values()));
    return admin;
  }
}
