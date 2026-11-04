package com.phytion.dashboard.controller;

import java.util.Map;

import com.phytion.dashboard.adapter.ci.CiToolException;
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

    /** An external tool is down, a token is wrong, ...: answer 502 (bad gateway) instead of a generic 500. */
    @ExceptionHandler(CiToolException.class)
    public ResponseEntity<Map<String, String>> ciToolFailed(CiToolException e) {
        log.warn("CI tool request failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }
}
