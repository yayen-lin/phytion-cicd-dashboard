package com.phytion.dashboard.controller;

import java.util.Map;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.adapter.code.CodeToolException;
import com.phytion.dashboard.service.PipelineNotFoundException;
import com.phytion.dashboard.service.RepoNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns exceptions from any controller into HTTP answers.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // 502 when an external tool is down
    @ExceptionHandler(CiToolException.class)
    public ResponseEntity<Map<String, String>> ciToolFailed(CiToolException e) {
        log.warn("CI tool request failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }

    // 502 when an external tool is down
    @ExceptionHandler(CodeToolException.class)
    public ResponseEntity<Map<String, String>> codeToolFailed(CodeToolException e) {
        log.warn("Code tool request failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }

    /// 404 when requesting an external that isn't configured
    @ExceptionHandler({PipelineNotFoundException.class, RepoNotFoundException.class})
    public ResponseEntity<Map<String, String>> notConfigured(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
}
