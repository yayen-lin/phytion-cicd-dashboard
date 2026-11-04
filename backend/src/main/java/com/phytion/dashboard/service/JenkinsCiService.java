package com.phytion.dashboard.service;

import java.util.List;
import java.util.Optional;

import com.phytion.dashboard.adapter.ci.jenkins.JenkinsAdapter;
import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.JenkinsCiBuildEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * CI builds of the configured job: listing them, and starting new ones (recorded in the audit log).
 * Works with whichever CI tool the CiAdapter connects to.
 */
@Service
@RequiredArgsConstructor
public class JenkinsCiService {

    private final JenkinsAdapter ci;
    private final AuditService audit;

    @Value("${dashboard.ci.job}")
    private final String job;

    public String job() {
        return job;
    }

    public List<JenkinsCiBuildEntry> listBuilds() {
        return ci.listBuilds(job);
    }

    /**
     * Starts a build of the job and records it in the audit log.
     *
     * @param username who started it
     * @return the new build's id, or empty when the CI tool hasn't started the build yet
     * @throws CiToolException when the CI tool can't be reached or refuses
     */
    public Optional<String> triggerBuild(String username) {
        Optional<String> buildId;
        try {
            buildId = ci.triggerBuild(job);
        } catch (CiToolException e) {
            audit.add(username, AuditEntry.ActionType.TRIGGER_BUILD, job, AuditEntry.ActionResult.FAILED, null,
                    e.getMessage());
            throw e;
        }
        audit.add(username, AuditEntry.ActionType.TRIGGER_BUILD, job, AuditEntry.ActionResult.SUCCESS,
                buildId.orElse(null), buildId.isPresent() ? null : "Queued, but the CI tool hadn't started it yet");
        return buildId;
    }
}
