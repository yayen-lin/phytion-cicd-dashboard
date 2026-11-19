package com.phytion.dashboard.service;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String username) {
        super("Unknown user: " + username);
    }
}
