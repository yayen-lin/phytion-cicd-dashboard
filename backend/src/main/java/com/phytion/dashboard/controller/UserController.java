package com.phytion.dashboard.controller;

import java.util.List;

import com.phytion.dashboard.auth.ActionAccess;
import com.phytion.dashboard.model.DashboardUser;
import com.phytion.dashboard.model.UserInfo;
import com.phytion.dashboard.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Managing dashboard users. Admin only.
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ActionAccess actionAccess;

    @GetMapping("/api/users")
    public List<UserInfo> listUsers(Authentication authentication) {
        actionAccess.checkIsAdmin(authentication);
        return userService.listUsers();
    }

    @PostMapping("/api/users")
    public UserInfo addUser(@RequestBody @Valid AddUserRequest request, Authentication authentication) {
        actionAccess.checkIsAdmin(authentication);
        return userService.addUser(request.username(), request.password(), request.role(), authentication.getName());
    }

    @PutMapping("/api/users/{username}/role")
    public UserInfo changeRole(@PathVariable String username, @RequestBody @Valid ChangeRoleRequest request,
                               Authentication authentication) {
        actionAccess.checkIsAdmin(authentication);
        return userService.changeRole(username, request.role(), authentication.getName());
    }

    @PutMapping("/api/users/{username}/active")
    public UserInfo changeActive(@PathVariable String username, @RequestBody @Valid ChangeActiveRequest request,
                                 Authentication authentication) {
        actionAccess.checkIsAdmin(authentication);
        return userService.changeActive(username, request.active(), authentication.getName());
    }

    /**
     * @param password 8 to 72 characters (BCrypt reads at most 72)
     */
    public record AddUserRequest(@NotBlank @Size(max = 100) String username,
                                 @NotNull @Size(min = 8, max = 72) String password,
                                 @NotNull DashboardUser.Role role) {
    }

    public record ChangeRoleRequest(@NotNull DashboardUser.Role role) {
    }

    public record ChangeActiveRequest(@NotNull Boolean active) {
    }
}
