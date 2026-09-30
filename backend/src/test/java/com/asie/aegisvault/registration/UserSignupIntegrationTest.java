package com.asie.aegisvault.registration;

import com.asie.aegisvault.User.AccountStatus;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:signup-integration;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.file.name="
})
@AutoConfigureMockMvc
class UserSignupIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    @BeforeEach
    void clearAccounts() {
        users.deleteAllInBatch();
    }

    @Test
    void signupWithPaddedUsernameCanLoginWithTheSameInput() throws Exception {
        String password = " Demo!pass123 ";
        mvc.perform(post("/user/signup").with(csrf())
                        .param("nickname", "  new-user  ").param("email", "new@example.com")
                        .param("password", password).param("passwordConfirm", password)
                        .param("position", "ADMIN").param("accountStatus", "LOCKED"))
                .andExpect(redirectedUrl("/"));
        mvc.perform(post("/user/login").with(csrf())
                        .param("username", "  new-user  ").param("password", password))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("new-user"));

        User created = users.findByNickname("new-user").orElseThrow();
        assertEquals(Position.STAFF, created.getPosition());
        assertEquals(AccountStatus.ACTIVE, created.getAccountStatus());
        assertNotEquals(password, created.getPassword());
        assertTrue(encoder.matches(password, created.getPassword()));
        assertFalse(encoder.matches(password.trim(), created.getPassword()));
    }

    @Test
    void paddingCannotBypassUsernameUniqueness() throws Exception {
        users.saveAndFlush(new User("existing", "existing@example.com", encoder.encode("Demo!pass123")));
        mvc.perform(post("/user/signup").with(csrf())
                        .param("nickname", " existing ").param("email", "other@example.com")
                        .param("password", "Demo!pass123").param("passwordConfirm", "Demo!pass123"))
                .andExpect(status().isOk()).andExpect(view().name("Signup"))
                .andExpect(content().string(containsString("이미 사용중인 닉네임입니다")));
        assertEquals(1, users.count());
    }
}
