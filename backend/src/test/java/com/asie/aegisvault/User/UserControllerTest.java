package com.asie.aegisvault.User;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

// 실제 DB에는 접근하지 않고 폼 검증, 서비스 호출 여부, 템플릿 출력을 확인합니다.
@ExtendWith(MockitoExtension.class)
class UserControllerTest {
    @Mock
    private UserService userService;

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode("HTML");
        templateResolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);
        ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
        viewResolver.setTemplateEngine(templateEngine);
        viewResolver.setCharacterEncoding("UTF-8");

        mockMvc = standaloneSetup(new UserController(userService))
                .setValidator(validator)
                .setViewResolvers(viewResolver)
                .build();
        viewResolver.setApplicationContext(mockMvc.getDispatcherServlet().getWebApplicationContext());
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void signupPageContainsPasswordConfirmation() throws Exception {
        mockMvc.perform(get("/user/signup"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("signupRequest"))
                .andExpect(content().string(containsString("name=\"passwordConfirm\"")));
        verifyNoInteractions(userService);
    }

    @Test
    void matchingPasswordsCallServiceOnce() throws Exception {
        mockMvc.perform(signup("demo-user", "demo@example.com", "demo-password", "demo-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
        verify(userService).create("demo-user", "demo@example.com", "demo-password");
    }

    @Test
    void mismatchingPasswordsReturnVisibleErrorWithoutSaving() throws Exception {
        mockMvc.perform(signup("demo-user", "demo@example.com", "demo-password", "different-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("Signup"))
                .andExpect(model().attributeHasFieldErrors("signupRequest", "passwordConfirm"))
                .andExpect(content().string(containsString("비밀번호가 일치하지 않습니다")))
                .andExpect(content().string(containsString("value=\"demo-user\"")))
                .andExpect(content().string(not(containsString("demo-password"))))
                .andExpect(content().string(not(containsString("different-password"))));
        verifyNoInteractions(userService);
    }

    @Test
    void missingConfirmationDoesNotSave() throws Exception {
        mockMvc.perform(post("/user/signup")
                        .param("nickname", "demo-user")
                        .param("email", "demo@example.com")
                        .param("password", "demo-password"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("signupRequest", "passwordConfirm"));
        verifyNoInteractions(userService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankConfirmationDoesNotSave(String confirmation) throws Exception {
        mockMvc.perform(signup("demo-user", "demo@example.com", "demo-password", confirmation))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("signupRequest", "passwordConfirm"));
        verifyNoInteractions(userService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void matchingBlankPasswordsStillDoNotSave(String password) throws Exception {
        mockMvc.perform(signup("demo-user", "demo@example.com", password, password))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("signupRequest", "password", "passwordConfirm"));
        verifyNoInteractions(userService);
    }

    @ParameterizedTest
    @CsvSource({"nickname,''", "nickname,'   '", "email,''", "email,'   '", "email,invalid-email"})
    void invalidAccountFieldsDoNotSave(String field, String value) throws Exception {
        String nickname = field.equals("nickname") ? value : "demo-user";
        String email = field.equals("email") ? value : "demo@example.com";
        mockMvc.perform(signup(nickname, email, "demo-password", "demo-password"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("signupRequest", field));
        verifyNoInteractions(userService);
    }

    @Test
    void passwordsAreNotTrimmedBeforeComparison() throws Exception {
        mockMvc.perform(signup("demo-user", "demo@example.com", "demo-password ", "demo-password"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("signupRequest", "passwordConfirm"));
        verifyNoInteractions(userService);
    }

    @Test
    void matchingPasswordsKeepOriginalSpaces() throws Exception {
        mockMvc.perform(signup("demo-user", "demo@example.com", " demo-password ", " demo-password "))
                .andExpect(status().is3xxRedirection());
        verify(userService).create("demo-user", "demo@example.com", " demo-password ");
    }

    @Test
    void duplicateAccountMessageIsShownWithoutEchoingPassword() throws Exception {
        when(userService.create("demo-user", "demo@example.com", "demo-password"))
                .thenThrow(new IllegalArgumentException("이미 사용중인 닉네임입니다"));
        mockMvc.perform(signup("demo-user", "demo@example.com", "demo-password", "demo-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("Signup"))
                .andExpect(content().string(containsString("이미 사용중인 닉네임입니다")))
                .andExpect(content().string(not(containsString("demo-password"))));
    }

    private MockHttpServletRequestBuilder signup(String nickname, String email,
                                                 String password, String confirmation) {
        return post("/user/signup")
                .param("nickname", nickname)
                .param("email", email)
                .param("password", password)
                .param("passwordConfirm", confirmation);
    }
}
