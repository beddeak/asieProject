package com.asie.aegisvault.Department;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

// 실제 DB를 사용하지 않는 부서 생성 화면 렌더링 테스트입니다.
@ExtendWith(MockitoExtension.class)
class DepartmentControllerTest {
    @Mock
    private DepartmentService departmentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
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

        mockMvc = standaloneSetup(new DepartmentController(departmentService))
                .setViewResolvers(viewResolver)
                .build();
        viewResolver.setApplicationContext(mockMvc.getDispatcherServlet().getWebApplicationContext());
    }

    @Test
    void createPageRendersInputsAndFormAction() throws Exception {
        mockMvc.perform(get("/dep/create"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/dep/create\"")))
                .andExpect(content().string(containsString("name=\"name\"")))
                .andExpect(content().string(containsString("name=\"description\"")))
                .andExpect(content().string(containsString("/css/departmentcreate.css")));
        verifyNoInteractions(departmentService);
    }

    @Test
    void submittedFieldsReachExistingService() throws Exception {
        mockMvc.perform(post("/dep/create")
                        .param("name", "연구개발본부")
                        .param("description", "설계 문서 작성"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"연구개발본부\"")));
        verify(departmentService).create("연구개발본부", "설계 문서 작성");
    }

    @Test
    void duplicateErrorIsShownAndInputIsEscaped() throws Exception {
        when(departmentService.create("연구개발본부", "<script>test</script>"))
                .thenThrow(new IllegalArgumentException("이미 있는 부서이름입니다"));
        mockMvc.perform(post("/dep/create")
                        .param("name", "연구개발본부")
                        .param("description", "<script>test</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이미 있는 부서이름입니다")))
                .andExpect(content().string(containsString("&lt;script&gt;test&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>test</script>"))));
    }
}
