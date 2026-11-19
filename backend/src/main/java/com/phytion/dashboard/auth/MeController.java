package com.phytion.dashboard.auth;

import com.phytion.dashboard.model.UserInfo;
import com.phytion.dashboard.repository.DashboardUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tells the frontend who is logged in. Returns 401 when nobody is.
 */
@RestController
@RequiredArgsConstructor
public class MeController {

    private final DashboardUserRepository userRepository;

    /**
     * The logged-in user, read fresh from the users table.
     * Returns 401 when the user is no longer in the table.
     */
    @GetMapping("/api/me")
    public UserInfo me(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .map(UserInfo::fromUser)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
