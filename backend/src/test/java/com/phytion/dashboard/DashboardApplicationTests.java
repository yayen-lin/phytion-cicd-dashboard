package com.phytion.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "DASHBOARD_USERS_FILE=src/test/resources/test-users.properties")
class DashboardApplicationTests {

    @Test
    void contextLoads() {
    }

}
