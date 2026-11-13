package com.phytion.dashboard.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.phytion.dashboard.adapter.operate.OperateToolException;
import com.phytion.dashboard.model.EnvironmentApp;
import com.phytion.dashboard.model.EnvironmentStatus;
import com.phytion.dashboard.service.InvalidDeployException;
import com.phytion.dashboard.service.KubernetesEnvironmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests the HTTP side only (login, JSON, status codes), with a fake KubernetesEnvironmentService.
 */
@SpringBootTest(properties = "DASHBOARD_USERS_FILE=src/test/resources/test-users.properties")
@AutoConfigureMockMvc
class KubernetesEnvironmentControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private KubernetesEnvironmentService environmentService;

    @Test
    void environmentsNeedLogin() throws Exception {
        mvc.perform(get("/api/deploy/environments")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void environmentsPassTheFiltersAndReturnEachAppsState() throws Exception {
        when(environmentService.listEnvironments("staging", null)).thenReturn(List.of(new EnvironmentStatus("staging",
                List.of(new EnvironmentApp("sample-app", "1.0.1", "a4dad00", null, 1, 1, true,
                                Instant.parse("2026-11-13T18:31:00Z"), "andy", "3"),
                        EnvironmentApp.notDeployed("dummy-app")))));

        mvc.perform(get("/api/deploy/environments").param("environment", "staging"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environments[0].environment").value("staging"))
                .andExpect(jsonPath("$.environments[0].apps[0].version").value("1.0.1"))
                .andExpect(jsonPath("$.environments[0].apps[0].healthy").value(true))
                .andExpect(jsonPath("$.environments[0].apps[0].deployedBy").value("andy"))
                .andExpect(jsonPath("$.environments[0].apps[0].deployedAt").value("2026-11-13T18:31:00Z"))
                .andExpect(jsonPath("$.environments[0].apps[1].appId").value("dummy-app"))
                .andExpect(jsonPath("$.environments[0].apps[1].version").isEmpty());
    }

    @Test
    @WithMockUser
    void unknownEnvironmentGives400WithTheReason() throws Exception {
        when(environmentService.listEnvironments("test", null))
                .thenThrow(new InvalidDeployException("Environments are dev, staging, prod, got: test"));

        mvc.perform(get("/api/deploy/environments").param("environment", "test"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Environments are dev, staging, prod, got: test"));
    }

    @Test
    @WithMockUser
    void kubernetesDownGives502WithTheReason() throws Exception {
        when(environmentService.listEnvironments(null, null))
                .thenThrow(new OperateToolException("Kubernetes not reachable"));

        mvc.perform(get("/api/deploy/environments"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Kubernetes not reachable"));
    }
}
