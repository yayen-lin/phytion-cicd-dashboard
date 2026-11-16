package com.phytion.dashboard.controller;

import com.phytion.dashboard.model.History;
import com.phytion.dashboard.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The History page: every action taken through the dashboard, with its build
 */
@RestController
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    /**
     * The recent audit log, newest first, each entry with the build it started.
     * e.g. -> {entries: [{entry: {actionType: DEPLOY, target: sample-app, buildId: "3", ...}, build: {...}}], unavailable: []}
     */
    @GetMapping("/api/history")
    public History getHistory() {
        return historyService.listHistory();
    }
}
