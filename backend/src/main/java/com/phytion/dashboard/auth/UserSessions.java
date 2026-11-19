package com.phytion.dashboard.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * The open login sessions of each user.
 */
@Component
@RequiredArgsConstructor
public class UserSessions {

    private final SessionRegistry sessionRegistry;

    /**
     * Ends every open session of a user and makes them log in again for changes to take effect.
     */
    public void endSessions(String username) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof UserDetails user && user.getUsername().equals(username))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .forEach(session -> session.expireNow());
    }
}
