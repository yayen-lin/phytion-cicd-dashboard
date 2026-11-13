package com.phytion.dashboard.controller;

import java.util.Map;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.adapter.code.CodeToolException;
import com.phytion.dashboard.adapter.operate.OperateToolException;
import com.phytion.dashboard.adapter.release.ReleaseToolException;
import com.phytion.dashboard.service.AppNotFoundException;
import com.phytion.dashboard.service.InvalidDeployException;
import com.phytion.dashboard.service.InvalidReleaseException;
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

    // 502 when an external tool is down
    @ExceptionHandler(ReleaseToolException.class)
    public ResponseEntity<Map<String, String>> releaseToolFailed(ReleaseToolException e) {
        log.warn("Release tool request failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }

    // 502 when an external tool is down
    @ExceptionHandler(OperateToolException.class)
    public ResponseEntity<Map<String, String>> operateToolFailed(OperateToolException e) {
        log.warn("Operate tool request failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
    }

    // 400 when a release is asked for with a bad version or commit
    @ExceptionHandler(InvalidReleaseException.class)
    public ResponseEntity<Map<String, String>> invalidRelease(InvalidReleaseException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    // 400 when a deploy is asked for with a version that isn't released or an environment that doesn't exist
    @ExceptionHandler(InvalidDeployException.class)
    public ResponseEntity<Map<String, String>> invalidDeploy(InvalidDeployException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    // 404 when asked about an app that isn't configured
    @ExceptionHandler(AppNotFoundException.class)
    public ResponseEntity<Map<String, String>> notConfigured(AppNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
}
