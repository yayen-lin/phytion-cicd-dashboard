package com.phytion.dashboard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.DashboardUser;
import com.phytion.dashboard.repository.AuditEntryRepository;
import com.phytion.dashboard.repository.DashboardUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests the users API against the in-memory database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DashboardUserRepository userRepository;

    @Autowired
    private AuditEntryRepository auditRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // admin_andy (ADMIN) and andy (DEVELOPER), both with password "password1"
    @BeforeEach
    void addUsers() {
        String hash = passwordEncoder.encode("password1");
        userRepository.save(new DashboardUser("admin_andy", hash, DashboardUser.Role.ADMIN, true, Instant.now()));
        userRepository.save(new DashboardUser("andy", hash, DashboardUser.Role.DEVELOPER, true, Instant.now()));
    }

    @AfterEach
    void removeUsersAndAudit() {
        userRepository.deleteAll();
        auditRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "admin_andy", roles = "ADMIN")
    void listUsersReturnsEveryUserWithoutPasswords() throws Exception {
        mvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin_andy"))
                .andExpect(jsonPath("$[1].username").value("andy"))
                .andExpect(jsonPath("$[1].role").value("DEVELOPER"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(username = "andy", roles = "DEVELOPER")
    void listUsersAsDeveloperGives403() throws Exception {
        mvc.perform(get("/api/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only an admin can manage users"));
    }

    @Test
    @WithMockUser(username = "admin_andy", roles = "ADMIN")
    void addUserHashesThePasswordAndRecordsIt() throws Exception {
        mvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "sam", "password": "password2", "role": "VIEWER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("sam"))
                .andExpect(jsonPath("$.role").value("VIEWER"))
                .andExpect(jsonPath("$.active").value(true));

        DashboardUser sam = userRepository.findByUsername("sam").orElseThrow();
        assertThat(sam.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches("password2", sam.getPasswordHash())).isTrue();
        assertThat(auditRepository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getUsername()).isEqualTo("admin_andy");
            assertThat(entry.getActionType()).isEqualTo(AuditEntry.ActionType.ADD_USER);
            assertThat(entry.getTarget()).isEqualTo("sam");
        });
    }

    @Test
    @WithMockUser(username = "admin_andy", roles = "ADMIN")
    void addUserWithShortPasswordGives400() throws Exception {
        mvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "sam", "password": "short", "role": "VIEWER"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin_andy", roles = "ADMIN")
    void addUserWithTakenUsernameGives400() throws Exception {
        mvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "andy", "password": "password2", "role": "VIEWER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username andy is taken"));
    }

    @Test
    @WithMockUser(username = "admin_andy", roles = "ADMIN")
    void changeRoleSavesAndRecordsIt() throws Exception {
        mvc.perform(put("/api/users/andy/role").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        assertThat(userRepository.findByUsername("andy").orElseThrow().getRole()).isEqualTo(DashboardUser.Role.ADMIN);
        assertThat(auditRepository.findAll()).singleElement()
                .satisfies(entry -> assertThat(entry.getDetails()).isEqualTo("DEVELOPER to ADMIN"));
    }

    @Test
    @WithMockUser(username = "admin_andy", roles = "ADMIN")
    void changeOwnRoleSavesIt() throws Exception {
        mvc.perform(put("/api/users/admin_andy/role").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "VIEWER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEWER"));

        assertThat(userRepository.findByUsername("admin_andy").orElseThrow().getRole())
                .isEqualTo(DashboardUser.Role.VIEWER);
    }

    @Test
    @WithMockUser(username = "admin_andy", roles = "ADMIN")
    void changeRoleOfUnknownUserGives404() throws Exception {
        mvc.perform(put("/api/users/nobody/role").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "VIEWER"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin_andy", roles = "ADMIN")
    void deactivateThenReactivateRecordsBoth() throws Exception {
        mvc.perform(put("/api/users/andy/active").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(put("/api/users/andy/active").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        assertThat(auditRepository.findAll()).extracting(AuditEntry::getActionType)
                .containsExactlyInAnyOrder(AuditEntry.ActionType.DEACTIVATE_USER, AuditEntry.ActionType.REACTIVATE_USER);
    }

    @Test
    void deactivatingEndsTheUsersSession() throws Exception {
        // andy and admin_andy log in
        MockHttpSession andySession = login("andy");
        MockHttpSession adminSession = login("admin_andy");
        mvc.perform(get("/api/me").session(andySession)).andExpect(status().isOk());

        // admin_andy deactivates andy
        mvc.perform(put("/api/users/andy/active").session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active": false}
                                """))
                .andExpect(status().isOk());

        // andy's next request is refused
        mvc.perform(get("/api/me").session(andySession)).andExpect(status().isUnauthorized());
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/api/login").user(username).password("password1"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }
}
