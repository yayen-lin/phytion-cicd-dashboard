package com.phytion.dashboard.model;

import java.time.Instant;

/**
 * What the dashboard shows about a user.
 *
 * @param username  login name
 * @param role      user role
 * @param active    false once an admin deactivated them
 * @param createdAt when the user was added
 */
public record UserInfo(String username, DashboardUser.Role role, boolean active, Instant createdAt) {
    public static UserInfo fromUser(DashboardUser user) {
        return new UserInfo(user.getUsername(), user.getRole(), user.isActive(), user.getCreatedAt());
    }
}
