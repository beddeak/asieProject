package com.asie.aegisvault.User;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.Document.Document;
import com.asie.aegisvault.Document.DocumentService;
import com.asie.aegisvault.Document.DocumentVersion;
import com.asie.aegisvault.config.SecurityConfig;
import com.asie.aegisvault.security.UserAccessPolicy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

// 실제 홈페이지·템플릿·보안 필터를 검증하며 DB 조회만 mock으로 대체합니다.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HomeControllerTest {
  private AnnotationConfigWebApplicationContext context;
  private MockMvc mvc;
  private UserRepository users;
  private DocumentService documents;
  private String passwordHash;
  private User member;
  private Department research;

  @BeforeAll
  void setUpContext() {
    context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(PageTestConfig.class);
    context.refresh();
    users = context.getBean(UserRepository.class);
    documents = context.getBean(DocumentService.class);
    passwordHash = context.getBean(PasswordEncoder.class).encode("Home!pass123");
    mvc = webAppContextSetup(context).apply(springSecurity()).build();
  }

  @BeforeEach
  void resetMocks() {
    reset(users, documents);
    research = new Department("연구개발본부", "테스트 부서");
    ReflectionTestUtils.setField(research, "id", 3L);
    member = new User("member", "member@example.com", passwordHash);
    ReflectionTestUtils.setField(member, "id", 7L);
    member.assign(Position.STAFF, research);
    when(users.findByNickname("member")).thenReturn(Optional.of(member));
    when(documents.accessibleDocuments(7L, 0, 6, "")).thenReturn(Page.empty());
  }

  @AfterAll
  void closeContext() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  void anonymousHomeRequestRequiresLogin() throws Exception {
    mvc.perform(get("/"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/user/login"));
    verifyNoInteractions(users, documents);
  }

  @Test
  void staffHomeShowsProfileAndAllowedLinksInsteadOfRedirecting() throws Exception {
    mvc.perform(get("/").with(user("member").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(view().name("home"))
        .andExpect(model().attribute("isAdmin", false))
        .andExpect(model().attribute("canWrite", true))
        .andExpect(model().attribute("canReview", false))
        .andExpect(content().string(containsString("member")))
        .andExpect(content().string(containsString("연구개발본부")))
        .andExpect(content().string(containsString("사원")))
        .andExpect(content().string(containsString("href=\"/document/list\"")))
        .andExpect(content().string(containsString("href=\"/document/new\"")))
        .andExpect(content().string(not(containsString("href=\"/document/review\""))))
        .andExpect(content().string(not(containsString("href=\"/admin/users\""))))
        .andExpect(content().string(not(containsString(passwordHash))));
    verify(documents).accessibleDocuments(7L, 0, 6, "");
  }

  @Test
  void managerHomeOffersReviewWhenDepartmentIsAssigned() throws Exception {
    member.assign(Position.MANAGER, research);
    mvc.perform(get("/").with(user("member")))
        .andExpect(status().isOk())
        .andExpect(model().attribute("canReview", true))
        .andExpect(content().string(containsString("href=\"/document/review\"")))
        .andExpect(content().string(not(containsString("href=\"/admin/users\""))));
  }

  @Test
  void unassignedManagerCannotOpenWritingOrReviewFromHome() throws Exception {
    member.assign(Position.MANAGER, null);
    mvc.perform(get("/").with(user("member")))
        .andExpect(status().isOk())
        .andExpect(view().name("home"))
        .andExpect(model().attribute("canWrite", false))
        .andExpect(model().attribute("canReview", false))
        .andExpect(content().string(not(containsString("href=\"/document/new\""))))
        .andExpect(content().string(not(containsString("href=\"/document/review\""))));
  }

  @Test
  void adminWithoutDepartmentGetsHomeAndAdministrationLinks() throws Exception {
    member.assign(Position.ADMIN, null);
    mvc.perform(get("/").with(user("member").roles("STAFF")))
        .andExpect(status().isOk())
        .andExpect(view().name("home"))
        .andExpect(model().attribute("isAdmin", true))
        .andExpect(model().attribute("canWrite", false))
        .andExpect(model().attribute("canReview", true))
        .andExpect(content().string(containsString("관리자")))
        .andExpect(content().string(containsString("href=\"/admin/users\"")))
        .andExpect(content().string(containsString("href=\"/admin/activity\"")))
        .andExpect(content().string(containsString("href=\"/document/review\"")));
  }

  @ParameterizedTest
  @EnumSource(
      value = AccountStatus.class,
      names = {"BAN", "LOCKED", "DELETED"})
  void inactiveAccountsCannotViewHome(AccountStatus status) throws Exception {
    member.changeAccountStatus(status);
    mvc.perform(get("/").with(user("member"))).andExpect(status().isForbidden());
    verifyNoInteractions(documents);
  }

  @Test
  void missingCurrentAccountCannotViewHome() throws Exception {
    when(users.findByNickname("member")).thenReturn(Optional.empty());
    mvc.perform(get("/").with(user("member"))).andExpect(status().isForbidden());
    verifyNoInteractions(documents);
  }

  @Test
  void userAndDocumentTextIsEscapedAndRecentLinkUsesDocumentId() throws Exception {
    User author = new User("<script>profile</script>", "safe@example.com", passwordHash);
    ReflectionTestUtils.setField(author, "id", 7L);
    author.assign(Position.STAFF, research);
    when(users.findByNickname("member")).thenReturn(Optional.of(author));
    Document document = new Document(author, research);
    ReflectionTestUtils.setField(document, "id", 42L);
    DocumentVersion version = new DocumentVersion(document, 1, "<script>title</script>", "본문");
    ReflectionTestUtils.setField(version, "id", 99L);
    ReflectionTestUtils.setField(version, "createdAt", LocalDateTime.of(2026, 10, 7, 10, 30));
    when(documents.accessibleDocuments(7L, 0, 6, ""))
        .thenReturn(
            new PageImpl<>(
                List.of(
                    new com.asie.aegisvault.Document.dto.DocumentListItem(
                        42L,
                        99L,
                        version.getTitle(),
                        version.getStatus(),
                        author.getNickname(),
                        research.getName(),
                        Position.STAFF,
                        1,
                        version.getCreatedAt(),
                        null,
                        null,
                        com.asie.aegisvault.Document.DocumentCategory.OTHER,
                        com.asie.aegisvault.security.SecurityClassification.INTERNAL,
                        false)),
                PageRequest.of(0, 6),
                17));
    mvc.perform(get("/").with(user("member")))
        .andExpect(status().isOk())
        .andExpect(model().attribute("documentCount", 17L))
        .andExpect(content().string(containsString("&lt;script&gt;profile&lt;/script&gt;")))
        .andExpect(content().string(containsString("&lt;script&gt;title&lt;/script&gt;")))
        .andExpect(content().string(not(containsString("<script>profile</script>"))))
        .andExpect(content().string(not(containsString("<script>title</script>"))))
        .andExpect(content().string(containsString("href=\"/document/detail/42\"")))
        .andExpect(content().string(not(containsString("href=\"/document/detail/99\""))));
  }

  @Test
  void homeSearchAndLogoutFormsUseImplementedRoutesAndCsrf() throws Exception {
    mvc.perform(get("/").with(user("member")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("action=\"/document/list\"")))
        .andExpect(content().string(containsString("name=\"q\"")))
        .andExpect(content().string(containsString("action=\"/logout\"")))
        .andExpect(content().string(containsString("name=\"_csrf\"")));
  }

  @Test
  void successfulLoginOpensHomeAndLogoutRequiresCsrf() throws Exception {
    var login =
        mvc.perform(
                post("/user/login")
                    .with(csrf())
                    .param("username", "member")
                    .param("password", "Home!pass123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"))
            .andReturn();
    MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
    assertNotNull(session);
    mvc.perform(get("/").session(session))
        .andExpect(status().isOk())
        .andExpect(view().name("home"));
    mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
    mvc.perform(post("/logout").session(session).with(csrf()))
        .andExpect(status().is3xxRedirection());
    assertTrue(session.isInvalid());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  @EnableWebSecurity
  @Import({SecurityConfig.class, com.asie.aegisvault.config.PasswordConfig.class})
  static class PageTestConfig {
    @Bean
    com.asie.aegisvault.audit.SecurityAudit securityAudit() {
      return mock(com.asie.aegisvault.audit.SecurityAudit.class);
    }

    @Bean
    UserRepository userRepository() {
      return mock(UserRepository.class);
    }

    @Bean
    DocumentService documentService() {
      return mock(DocumentService.class);
    }

    @Bean
    UserAccessPolicy userAccessPolicy() {
      return new UserAccessPolicy();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository repository) {
      return new CustomUserDetailsService(repository);
    }

    @Bean
    HomeController homeController(
        UserRepository repository, DocumentService service, UserAccessPolicy policy) {
      return new HomeController(repository, service, policy);
    }

    @Bean
    SpringTemplateEngine templateEngine() {
      ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
      resolver.setPrefix("templates/");
      resolver.setSuffix(".html");
      resolver.setTemplateMode("HTML");
      resolver.setCharacterEncoding("UTF-8");
      SpringTemplateEngine engine = new SpringTemplateEngine();
      engine.addDialect(new SpringSecurityDialect());
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
  }
}
