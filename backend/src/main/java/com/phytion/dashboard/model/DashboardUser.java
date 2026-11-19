package com.phytion.dashboard.model;

import java.time.Instant;

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

/**
 * A person who can log in to the dashboard. Named DashboardUser so it isn't confused with Spring Security's User.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class DashboardUser {

    // what a user may do: VIEWER looks, DEVELOPER acts outside prod, ADMIN does everything and manages users
    public enum Role {
        VIEWER,
        DEVELOPER,
        ADMIN
    }

    // user columns
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String passwordHash;
    private boolean active;
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    private Role role;

    public DashboardUser(String username, String passwordHash, Role role, boolean active, Instant createdAt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
        this.createdAt = createdAt;
    }

    public void changeRole(Role role) {
        this.role = role;
    }

    public void changeActive(boolean active) {
        this.active = active;
    }
}
