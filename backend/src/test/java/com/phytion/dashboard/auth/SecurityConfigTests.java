package com.phytion.dashboard.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.phytion.dashboard.model.DashboardUser;
import com.phytion.dashboard.repository.DashboardUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DashboardUserRepository userRepository;

    // an active admin and a deactivated developer, both with password "test" ({noop} stores it as is, tests only)
    @BeforeEach
    void addUsers() {
        userRepository.save(new DashboardUser("test", "{noop}test", DashboardUser.Role.ADMIN, true, Instant.now()));
        userRepository.save(new DashboardUser("gone", "{noop}test", DashboardUser.Role.DEVELOPER, false, Instant.now()));
    }

    @AfterEach
    void removeUsers() {
        userRepository.deleteAll();
    }

    @Test
    void statusIsPublicAndSetsCsrfCookie() throws Exception {
        mvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void statusGivesThePollIntervalFromTheConfig() throws Exception {
        mvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.pollIntervalMillis").value(3000));
    }

    @Test
    void meNeedsLogin() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(formLogin("/api/login").user("test").password("wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownUserIsRejected() throws Exception {
        mvc.perform(formLogin("/api/login").user("nobody").password("test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedUserIsRejected() throws Exception {
        mvc.perform(formLogin("/api/login").user("gone").password("test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginThenMeReturnsUser() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc
                .perform(formLogin("/api/login").user("test").password("test"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        mvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("test"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/logout")).andExpect(status().isForbidden());
    }
}
