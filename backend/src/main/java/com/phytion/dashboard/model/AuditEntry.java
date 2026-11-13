package com.phytion.dashboard.model;

import java.time.Instant;

import jakarta.annotation.Nonnull;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class AuditEntry {

    // action enums
    public enum ActionType {
        BUILD,
        RELEASE,
        DEPLOY
    }

    public enum ActionResult {
        SUCCESS,
        FAILED
    }

    // audit entry columns
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String target;
    private Instant createdAt;
    private String buildId;
    private String details;
    private String environment;
    private String version;

    @Enumerated(EnumType.STRING)
    private ActionType actionType;
    @Enumerated(EnumType.STRING)
    private ActionResult actionResult;

    public AuditEntry(String username, ActionType actionType, String target, Instant createdAt,
                      ActionResult actionResult, String buildId, String details, String environment, String version) {
        this.username = username;
        this.actionType = actionType;
        this.target = target;
        this.createdAt = createdAt;
        this.actionResult = actionResult;
        this.buildId = buildId;
        this.details = details;
        this.environment = environment;
        this.version = version;
    }
}
