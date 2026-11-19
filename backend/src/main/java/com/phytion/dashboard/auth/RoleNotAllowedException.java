package com.phytion.dashboard.auth;

/**
 * The user's role is not allowed to take this action
 */
public class RoleNotAllowedException extends RuntimeException {

    public RoleNotAllowedException(String message) {
        super(message);
    }
}
