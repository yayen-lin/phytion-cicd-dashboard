package com.phytion.dashboard.auth;

import com.phytion.dashboard.config.EnvironmentsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActionAccess {

    private final EnvironmentsProperties environmentsProperties;

    /**
     * Checks if the user may take an action.
     * Throws RoleNotAllowedException when the user is neither DEVELOPER nor ADMIN.
     */
    public void checkCanAct(Authentication authentication) {
        if (!hasRole(authentication, "ROLE_DEVELOPER") && !hasRole(authentication, "ROLE_ADMIN")) {
            throw new RoleNotAllowedException("Your role can't run builds, releases or deploys");
        }
    }

    /**
     * Checks if the user may change what runs in an environment.
     * Throws RoleNotAllowedException when the user can't act.
     */
    public void checkCanActIn(String environment, Authentication authentication) {
        checkCanAct(authentication);
        if (environmentsProperties.needsAdmin(environment) && !hasRole(authentication, "ROLE_ADMIN")) {
            throw new RoleNotAllowedException("Only an admin can change " + environment);
        }
    }

    /**
     * Checks the user is an admin
     * Throws RoleNotAllowedException when the user isn't an ADMIN.
     */
    public void checkIsAdmin(Authentication authentication) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            throw new RoleNotAllowedException("Only an admin can manage users");
        }
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(role::equals);
    }
}
