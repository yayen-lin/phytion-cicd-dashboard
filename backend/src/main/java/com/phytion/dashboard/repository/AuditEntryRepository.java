package com.phytion.dashboard.repository;

import java.util.List;
import java.util.Optional;

import com.phytion.dashboard.model.AuditEntry;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Database access for the audit log. Spring Data generates the queries from the method names, or runs the @Query.
 */
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    /** Newest first. A null filter matches every entry. */
    @Query("""
            SELECT e FROM AuditEntry e
            WHERE (:target IS NULL OR e.target = :target)
              AND (:actionType IS NULL OR e.actionType = :actionType)
              AND (:username IS NULL OR e.username = :username)
              AND (:actionResult IS NULL OR e.actionResult = :actionResult)
            ORDER BY e.id DESC
            """)
    List<AuditEntry> findFiltered(String target, AuditEntry.ActionType actionType, String username,
                                  AuditEntry.ActionResult actionResult, Limit limit);

    @Query("SELECT DISTINCT e.target FROM AuditEntry e ORDER BY e.target")
    List<String> findTargets();

    @Query("SELECT DISTINCT e.actionType FROM AuditEntry e ORDER BY e.actionType")
    List<AuditEntry.ActionType> findActionTypes();

    @Query("SELECT DISTINCT e.username FROM AuditEntry e ORDER BY e.username")
    List<String> findUsernames();

    Optional<AuditEntry> findFirstByTargetAndActionTypeAndBuildIdOrderByIdDesc(String target,
                                                                                AuditEntry.ActionType actionType,
                                                                                String buildId);
}
