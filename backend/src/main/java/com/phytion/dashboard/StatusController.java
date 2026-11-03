package com.phytion.dashboard;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple endpoint the frontend calls to check that the backend is running.
 */
@RestController
public class StatusController {

    @GetMapping("/api/status")
    public Map<String, String> status() {
        return Map.of("status", "ok");
    }
}
