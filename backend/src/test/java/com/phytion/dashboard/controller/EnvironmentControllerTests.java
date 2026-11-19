package com.phytion.dashboard.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests the environments API against the real config, so it also checks how dashboard.environments and
 * dashboard.confirm-environments are read.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EnvironmentControllerTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void environmentsNeedLogin() throws Exception {
        mvc.perform(get("/api/environments")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void environmentsComeFromTheConfigInOrderAndAllButDevNeedConfirmation() throws Exception {
        mvc.perform(get("/api/environments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("dev"))
                .andExpect(jsonPath("$[0].needsConfirmation").value(false))
                .andExpect(jsonPath("$[1].name").value("staging"))
                .andExpect(jsonPath("$[1].needsConfirmation").value(true))
                .andExpect(jsonPath("$[2].name").value("prod"))
                .andExpect(jsonPath("$[2].needsConfirmation").value(true))
                .andExpect(jsonPath("$[1].needsAdmin").value(false))
                .andExpect(jsonPath("$[2].needsAdmin").value(true));
    }
}
