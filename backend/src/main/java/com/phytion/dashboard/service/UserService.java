package com.phytion.dashboard.service;

import java.time.Instant;
import java.util.List;

import com.phytion.dashboard.auth.UserSessions;
import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.DashboardUser;
import com.phytion.dashboard.model.UserInfo;
import com.phytion.dashboard.repository.DashboardUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adding users, changing their role, and deactivating them. Each change is recorded in the audit log.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final DashboardUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSessions userSessions;
    private final AuditService audit;

    /**
     * List every user with the oldest first.
     */
    public List<UserInfo> listUsers() {
        return userRepository.findAll(Sort.by("id")).stream().map(UserInfo::fromUser).toList();
    }

    /**
     * Adds an active user with a hashed password.
     * Throws InvalidUserChangeException when the username is taken.
     */
    @Transactional
    public UserInfo addUser(String username, String password, DashboardUser.Role role, String adminUsername) {
        // check the username is free
        if (userRepository.findByUsername(username).isPresent()) {
            throw new InvalidUserChangeException("Username " + username + " is taken");
        }

        // save with the password hashed
        DashboardUser user = userRepository.save(new DashboardUser(username, passwordEncoder.encode(password), role,
                true, Instant.now()));

        // record it
        audit.add(adminUsername, AuditEntry.ActionType.ADD_USER, username, AuditEntry.ActionResult.SUCCESS, null,
                "as " + role, null, null);
        return UserInfo.fromUser(user);
    }

    /**
     * Gives a user another role and ends their sessions, so it applies on their next login.
     * Throws UserNotFoundException for an unknown user, InvalidUserChangeException when the admin changes themselves.
     */
    @Transactional
    public UserInfo changeRole(String username, DashboardUser.Role role, String adminUsername) {
        // find the user, who isn't the admin
        DashboardUser user = findByUsername(username, adminUsername);
        DashboardUser.Role oldRole = user.getRole();
        if (oldRole == role) {
            return UserInfo.fromUser(user);
        }

        // change it, log them out and record it
        user.changeRole(role);
        userSessions.endSessions(username);
        audit.add(adminUsername, AuditEntry.ActionType.CHANGE_ROLE, username, AuditEntry.ActionResult.SUCCESS, null,
                oldRole + " to " + role, null, null);
        return UserInfo.fromUser(user);
    }

    /**
     * Deactivates (active false) or reactivates (true) a user. Deactivating ends their sessions.
     * Throws UserNotFoundException for an unknown user, InvalidUserChangeException when the admin changes themselves.
     */
    @Transactional
    public UserInfo changeActive(String username, boolean active, String adminUsername) {
        // find the user, who isn't the admin
        DashboardUser user = findByUsername(username, adminUsername);
        if (user.isActive() == active) {
            return UserInfo.fromUser(user);
        }

        // change it, log them out and record it
        user.changeActive(active);
        if (!active) {
            userSessions.endSessions(username);
        }
        audit.add(adminUsername, active ? AuditEntry.ActionType.REACTIVATE_USER : AuditEntry.ActionType.DEACTIVATE_USER,
                username, AuditEntry.ActionResult.SUCCESS, null, null, null, null);
        return UserInfo.fromUser(user);
    }

    private DashboardUser findByUsername(String username, String adminUsername) {
        return userRepository.findByUsername(username).orElseThrow(() -> new UserNotFoundException(username));
    }
}
