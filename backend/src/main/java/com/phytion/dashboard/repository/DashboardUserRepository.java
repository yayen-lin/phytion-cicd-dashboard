package com.phytion.dashboard.repository;

import java.util.Optional;

import com.phytion.dashboard.model.DashboardUser;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for dashboard users.
 */
public interface DashboardUserRepository extends JpaRepository<DashboardUser, Long> {

    Optional<DashboardUser> findByUsername(String username);
}
