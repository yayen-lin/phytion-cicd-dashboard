package com.phytion.dashboard.controller;

import java.util.Map;

import com.phytion.dashboard.config.FrontendProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StatusController {

    private final FrontendProperties frontend;

    /**
     * Says the backend is up, with the settings the frontend reads once when it starts.
     */
    @GetMapping("/api/status")
    public Map<String, Object> status() {
        return Map.of("status", "ok", "pollIntervalMillis", frontend.pollInterval().toMillis());
    }
}
