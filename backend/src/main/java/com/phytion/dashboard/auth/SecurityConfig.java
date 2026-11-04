package com.phytion.dashboard.auth;

import java.io.IOException;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

/**
 * Login setup: users come from a properties file, and the React app logs in
 * with a session cookie. Every /api endpoint except /api/status needs a login.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/status").permitAll()
                        .anyRequest().authenticated())
                // POST /api/login with form fields "username" and "password"
                .formLogin(form -> form
                        .loginProcessingUrl("/api/login")
                        .successHandler((request, response, authentication) -> response.setStatus(HttpStatus.OK.value()))
                        .failureHandler((request, response, exception) -> response.setStatus(HttpStatus.UNAUTHORIZED.value()))
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()))
                // Answer 401 instead of redirecting to a login page; the React app shows its own
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // CSRF token in the XSRF-TOKEN cookie; the React app sends it back in the X-XSRF-TOKEN header
                .csrf(csrf -> csrf.spa());
        return http.build();
    }

    /**
     * Loads users from a file with lines like: username={bcrypt}hash,ROLE_XX
     */
    @Bean
    UserDetailsService userDetailsService(
            @Value("${DASHBOARD_USERS_FILE:users.properties}") String usersFile) throws IOException {
        Properties users = PropertiesLoaderUtils.loadProperties(new FileSystemResource(usersFile));
        return new InMemoryUserDetailsManager(users);
    }
}
