package com.phytion.dashboard.repository;

import java.util.List;
import java.util.Optional;

import com.phytion.dashboard.model.AuditEntry;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Database access for the audit log.
 */
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    @Query("""
            SELECT e FROM AuditEntry e
            WHERE (:target IS NULL OR e.target = :target)
              AND (:actionType IS NULL OR e.actionType = :actionType)
              AND (:username IS NULL OR e.username = :username)
              AND (:actionResult IS NULL OR e.actionResult = :actionResult)
              AND (:buildId IS NULL OR e.buildId = :buildId)
              AND (:environment IS NULL OR e.environment = :environment)
              AND (:version IS NULL OR e.version = :version)
            ORDER BY e.createdAt DESC
            """)
    List<AuditEntry> findFiltered(String target, AuditEntry.ActionType actionType, String username,
                                  AuditEntry.ActionResult actionResult, String buildId, String environment,
                                  String version, Limit limit);

    Optional<AuditEntry> findFirstByTargetAndActionTypeAndBuildIdOrderByIdDesc(String target,
                                                                               AuditEntry.ActionType actionType,
                                                                               String buildId);
}
