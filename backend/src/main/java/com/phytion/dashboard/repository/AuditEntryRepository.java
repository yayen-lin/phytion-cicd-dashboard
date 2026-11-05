package com.phytion.dashboard.repository;

import java.util.List;
import java.util.Optional;

import com.phytion.dashboard.model.AuditEntry;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for the audit log. Spring Data generates the queries from the method names.
 */
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findByOrderByIdDesc(Limit limit);

    List<AuditEntry> findByTargetOrderByIdDesc(String target, Limit limit);

    Optional<AuditEntry> findFirstByTargetAndActionTypeAndBuildIdOrderByIdDesc(String target,
                                                                                AuditEntry.ActionType actionType,
                                                                                String buildId);
}
