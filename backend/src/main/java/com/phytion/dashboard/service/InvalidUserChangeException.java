package com.phytion.dashboard.service;

/**
 * A user change was refused
 */
public class InvalidUserChangeException extends RuntimeException {

    public InvalidUserChangeException(String message) {
        super(message);
    }
}
