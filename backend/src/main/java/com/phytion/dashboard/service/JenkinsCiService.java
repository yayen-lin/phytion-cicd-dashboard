package com.phytion.dashboard.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.adapter.ci.jenkins.JenkinsAdapter;
import com.phytion.dashboard.config.CiProperties;
import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.CiBuildDetails;
import com.phytion.dashboard.model.CiPipeline;
import com.phytion.dashboard.model.CiTestReport;
import com.phytion.dashboard.model.CiBuild;
import org.springframework.stereotype.Service;

/**
 * CI builds of the configured pipelines: listing them, and starting new ones (recorded in the audit log).
 */
@Service
public class JenkinsCiService {

    private static final String JENKINS = "jenkins";

    private final JenkinsAdapter ci;
    private final AuditService audit;
    private final List<CiPipeline> pipelines;

    public JenkinsCiService(JenkinsAdapter ci, AuditService audit, CiProperties properties) {
        // Jenkins is the only CI tool with an adapter so far: fail at startup rather than on the first request
        properties.pipelines().stream()
                .filter(pipeline -> !JENKINS.equals(pipeline.tool()))
                .findFirst()
                .ifPresent(pipeline -> {
                    throw new IllegalStateException("No adapter for CI tool '" + pipeline.tool()
                            + "' (pipeline " + pipeline.id() + "); only 'jenkins' is supported");
                });
        this.ci = ci;
        this.audit = audit;
        this.pipelines = properties.pipelines();
    }

    /** The configured pipelines, in config order. */
    public List<CiPipeline> pipelines() {
        return pipelines;
    }

    /**
     * @param pipelineId e.g. "jenkins:sample-app"
     * @throws PipelineNotFoundException when no configured pipeline has that id
     */
    public List<CiBuild> listBuilds(String pipelineId) {
        return ci.listBuilds(pipeline(pipelineId).job());
    }

    /**
     * One build of a pipeline. "Started by" is the dashboard user when the build was started through the dashboard
     * (the CI tool only knows the dashboard's own API user then), otherwise the CI tool's user.
     *
     * @param pipelineId e.g. "jenkins:sample-app"
     * @return the build, or empty when the CI tool has no such build
     * @throws PipelineNotFoundException when no configured pipeline has that id
     */
    public Optional<CiBuildDetails> getBuild(String pipelineId, String buildId) {
        CiPipeline pipeline = pipeline(pipelineId);
        return ci.getBuild(pipeline.job(), buildId).map(details -> audit.findBuildStarter(pipeline.id(), buildId)
                .map(username -> new CiBuildDetails(details.build(), username))
                .orElse(details));
    }

    /**
     * @param pipelineId e.g. "jenkins:sample-app"
     * @return the build's test results, or empty when it has none (or doesn't exist)
     * @throws PipelineNotFoundException when no configured pipeline has that id
     */
    public Optional<CiTestReport> getTestReport(String pipelineId, String buildId) {
        return ci.getTestReport(pipeline(pipelineId).job(), buildId);
    }

    /**
     * Starts a build of the pipeline's job and records it in the audit log.
     *
     * @param pipelineId e.g. "jenkins:sample-app"
     * @param username   who started it
     * @return the new build's id, or empty when the CI tool hasn't started the build yet
     * @throws PipelineNotFoundException when no configured pipeline has that id
     * @throws CiToolException           when the CI tool can't be reached or refuses
     */
    public Optional<String> triggerBuild(String pipelineId, String username) {
        CiPipeline pipeline = pipeline(pipelineId);
        Optional<String> buildId;
        try {
            buildId = ci.triggerBuild(pipeline.job(), Map.of());
        } catch (CiToolException e) {
            audit.add(username, AuditEntry.ActionType.TRIGGER_BUILD, pipeline.id(), AuditEntry.ActionResult.FAILED,
                    null, e.getMessage());
            throw e;
        }
        audit.add(username, AuditEntry.ActionType.TRIGGER_BUILD, pipeline.id(), AuditEntry.ActionResult.SUCCESS,
                buildId.orElse(null), buildId.isPresent() ? null : "Queued, but the CI tool hadn't started it yet");
        return buildId;
    }

    /**
     * Starts a build of the pipeline's job with parameters, for actions that start a build as one of their steps (e.g.
     * a release). Not recorded in the audit log: the action records itself, with the build's id.
     *
     * @param pipelineId e.g. "jenkins:sample-app"
     * @param parameters e.g. GIT_REF -> refs/tags/v1.4.0
     * @return the new build's id, or empty when the CI tool hasn't started the build yet
     * @throws PipelineNotFoundException when no configured pipeline has that id
     * @throws CiToolException           when the CI tool can't be reached or refuses
     */
    public Optional<String> startBuild(String pipelineId, Map<String, String> parameters) {
        return ci.triggerBuild(pipeline(pipelineId).job(), parameters);
    }

    private CiPipeline pipeline(String pipelineId) {
        return pipelines.stream()
                .filter(pipeline -> pipeline.id().equals(pipelineId))
                .findFirst()
                .orElseThrow(() -> new PipelineNotFoundException(pipelineId));
    }
}
