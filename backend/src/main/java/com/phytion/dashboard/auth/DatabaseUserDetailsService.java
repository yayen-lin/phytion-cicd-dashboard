package com.phytion.dashboard.auth;

import com.phytion.dashboard.model.DashboardUser;
import com.phytion.dashboard.repository.DashboardUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {

    private final DashboardUserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        // find the user
        DashboardUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user " + username));

        // the hash keeps its {bcrypt} prefix, so Spring picks the right encoder
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .disabled(!user.isActive())
                .build();
    }
}
