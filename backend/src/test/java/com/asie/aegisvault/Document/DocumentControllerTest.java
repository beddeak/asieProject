package com.asie.aegisvault.Document;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.UserRepository;
import com.asie.aegisvault.config.SecurityConfig;
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
import org.springframework.mock.web.MockServletContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
    }

    @AfterAll
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void anonymousUserIsSentToLogin() throws Exception {
        mockMvc.perform(get("/document/write"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/login"));
        verifyNoInteractions(documentService, userRepository);
    }

    @Test
    void emptyFormRendersWithDraftDefaultsAndCsrfToken() throws Exception {
        mockMvc.perform(get("/document/write").with(user("writer")))
                .andExpect(status().isOk())
                .andExpect(view().name("documentwrite"))
                .andExpect(model().hasNoErrors())
                .andExpect(content().string(containsString("action=\"/document/write\"")))
                .andExpect(content().string(containsString("name=\"title\"")))
                .andExpect(content().string(containsString("name=\"content\"")))
                .andExpect(content().string(containsString("name=\"versionNumber\" value=\"1\"")))
                .andExpect(content().string(containsString("name=\"status\" value=\"DRAFT\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("/css/documentwrite.css")))
                .andExpect(content().string(not(containsString("name=\"authorId\""))));
        verifyNoInteractions(documentService, userRepository);
    }

    @Test
    void stylesheetIsServedToLoggedInUser() throws Exception {
        mockMvc.perform(get("/css/documentwrite.css").with(user("writer")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"))
                .andExpect(content().string(containsString(".document-write-page")));
    }

    @Test
    void validFormUsesAuthenticatedAuthorAndShowsSuccess() throws Exception {
        mockAuthor();
        mockMvc.perform(write("시험 보고서", "시험 결과입니다.").param("authorId", "999"))
                .andExpect(status().isOk())
                .andExpect(view().name("documentwrite"))
                .andExpect(content().string(containsString("문서가 첫 번째 버전의 초안으로 저장되었습니다.")));
        verify(userRepository).findByNickname("writer");
        verify(documentService).create(1, "시험 보고서", "시험 결과입니다.", 7L);
    }

    @ParameterizedTest
    @CsvSource({"title,''", "title,'   '", "content,''", "content,'   '"})
    void blankFieldsRenderErrorsWithoutSaving(String field, String value) throws Exception {
        String title = field.equals("title") ? value : "유지할 제목";
        String contentValue = field.equals("content") ? value : "유지할 본문";
        mockMvc.perform(write(title, contentValue))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("documentCreateRequest", field))
                .andExpect(content().string(containsString("입력 내용을 확인해주세요.")))
                .andExpect(content().string(containsString(field.equals("title") ? "유지할 본문" : "유지할 제목")));
        verifyNoInteractions(documentService, userRepository);
    }

    @Test
    void titleOver50CharactersIsRejected() throws Exception {
        mockMvc.perform(write("가".repeat(51), "본문"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("documentCreateRequest", "title"))
                .andExpect(content().string(containsString("문서 제목은 50자 이하여야 합니다")));
        verifyNoInteractions(documentService, userRepository);
    }

    @Test
    void missingMetadataReturnsVisibleValidationErrors() throws Exception {
        mockMvc.perform(post("/document/write").with(user("writer")).with(csrf())
                        .param("title", "제목").param("content", "본문"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("documentCreateRequest", "versionNumber", "status"))
                .andExpect(content().string(containsString("버전 번호를 입력해주세요")))
                .andExpect(content().string(containsString("문서 상태를 선택해주세요")));
        verifyNoInteractions(documentService, userRepository);
    }

    @Test
    void invalidMetadataTypesReturnFormInsteadOfServerError() throws Exception {
        mockMvc.perform(post("/document/write").with(user("writer")).with(csrf())
                        .param("title", "제목").param("content", "본문")
                        .param("versionNumber", "not-a-number").param("status", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("documentCreateRequest", "versionNumber", "status"))
                .andExpect(view().name("documentwrite"));
        verifyNoInteractions(documentService, userRepository);
    }

    @Test
    void serviceErrorPreservesAndEscapesInput() throws Exception {
        mockAuthor();
        String title = "<b>제목</b>";
        String contentValue = "</textarea><script>alert(1)</script>";
        when(documentService.create(1, title, contentValue, 7L))
                .thenThrow(new IllegalArgumentException("소속 부서가 없습니다"));
        mockMvc.perform(write(title, contentValue))
                .andExpect(status().isOk())
                .andExpect(view().name("documentwrite"))
                .andExpect(content().string(containsString("소속 부서가 없습니다")))
                .andExpect(content().string(containsString("&lt;b&gt;제목&lt;/b&gt;")))
                .andExpect(content().string(containsString("&lt;/textarea&gt;&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }

    @Test
    void postWithoutCsrfTokenIsBlocked() throws Exception {
        mockMvc.perform(post("/document/write").with(user("writer"))
                        .param("title", "제목").param("content", "본문")
                        .param("versionNumber", "1").param("status", "DRAFT"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(documentService, userRepository);
    }

    @Test
    void anonymousDetailRequestIsSentToLogin() throws Exception {
        mockMvc.perform(get("/document/detail/42"))
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

        mockMvc.perform(get("/document/detail/42").with(user("writer"))
                        .param("viewerId", "999").param("position", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(view().name("documentdetail"))
                .andExpect(model().attribute("documentVersion", version))
                .andExpect(content().string(containsString("최신 시험 보고서")))
                .andExpect(content().string(containsString("승인된 열람 요청의 본문")))
                .andExpect(content().string(containsString("original-author")))
                .andExpect(content().string(containsString("연구개발본부")));

        verify(userRepository).findByNickname("writer");
        verify(documentService).documentdetail(42L, 7L);
    }

    @Test
    void forbiddenDetailRequestReturns403() throws Exception {
        mockAuthor();
        when(documentService.documentdetail(42L, 7L))
                .thenThrow(new AccessDeniedException("열람 권한이 없습니다."));

        mockMvc.perform(get("/document/detail/42").with(user("writer")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletedViewerCannotReadUsingAnExistingLoginSession() throws Exception {
        when(userRepository.findByNickname("writer")).thenReturn(Optional.empty());

        mockMvc.perform(get("/document/detail/42").with(user("writer")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(documentService);
    }

    @Test
    void missingDocumentReturns404() throws Exception {
        mockAuthor();
        when(documentService.documentdetail(42L, 7L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));

        mockMvc.perform(get("/document/detail/42").with(user("writer")))
                .andExpect(status().isNotFound());
    }

    private void mockAuthor() {
        User author = mock(User.class);
        when(author.getId()).thenReturn(7L);
        when(userRepository.findByNickname("writer")).thenReturn(Optional.of(author));
    }

    private MockHttpServletRequestBuilder write(String title, String contentValue) {
        return post("/document/write").with(user("writer")).with(csrf())
                .param("title", title).param("content", contentValue)
                .param("versionNumber", "1").param("status", "DRAFT");
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
            return new DocumentController(service, repository);
        }

        @Bean
        SpringTemplateEngine templateEngine() {
            ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
            resolver.setPrefix("templates/");
            resolver.setSuffix(".html");
            resolver.setTemplateMode("HTML");
            resolver.setCharacterEncoding("UTF-8");
            SpringTemplateEngine engine = new SpringTemplateEngine();
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
}
