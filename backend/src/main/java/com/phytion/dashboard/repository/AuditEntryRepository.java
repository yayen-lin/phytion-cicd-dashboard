package com.phytion.dashboard.repository;

import java.util.List;

import com.phytion.dashboard.model.AuditEntry;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for the audit log. Spring Data generates the queries from the method names.
 */
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findByOrderByIdDesc(Limit limit);
}
