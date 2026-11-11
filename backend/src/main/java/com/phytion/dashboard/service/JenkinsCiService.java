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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * CI builds of the configured pipelines: listing them, and starting new ones (recorded in the audit log).
 */
@Service
@RequiredArgsConstructor
public class JenkinsCiService {

    private final JenkinsAdapter ci;
    private final AuditService audit;
    private final CiProperties properties;

    /**
     * Return the configured pipelines in config order.
     */
    public List<CiPipeline> listPipelines() {
        return properties.pipelines();
    }

    /**
     * Lists out the builds of this pipelineId.
     *
     * @param pipelineId pipeline identifier
     * @throws PipelineNotFoundException when no configured pipeline identifier has that id
     */
    public List<CiBuild> listBuilds(String pipelineId) {
        return ci.listBuilds(pipeline(pipelineId).job());
    }

    /**
     * One build of a pipeline.
     *
     * @param pipelineId pipeline identifier
     * @return the build, or empty when the CI tool has no such build
     * @throws PipelineNotFoundException when no configured pipeline has that id
     */
    public Optional<CiBuildDetails> getBuild(String pipelineId, String buildId) {
        CiPipeline pipeline = pipeline(pipelineId);
        return ci.getBuild(pipeline.job(), buildId).map(details -> audit.findBuildStarter(pipeline.id(), buildId)
                .map(username -> new CiBuildDetails(details.build(), username, CiBuildDetails.DASHBOARD))
                .orElseGet(() -> new CiBuildDetails(details.build(), details.startedBy(),
                        details.startedBy() == null ? null : pipeline.tool())));
    }

    /**
     * @param pipelineId pipeline identifier
     * @return the build's test results, or empty when it has none (or doesn't exist)
     * @throws PipelineNotFoundException when no configured pipeline has that id
     */
    public Optional<CiTestReport> getTestReport(String pipelineId, String buildId) {
        return ci.getTestReport(pipeline(pipelineId).job(), buildId);
    }

    /**
     * Starts a build of the pipeline's job and records it in the audit log.
     *
     * @param pipelineId pipeline identifier
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
     * Starts a build of the pipeline's job with parameters.
     *
     * @param pipelineId pipeline identifier
     * @param parameters e.g. GIT_REF -> refs/tags/v1.4.0
     * @return the new build's id, or empty when the CI tool hasn't started the build yet
     * @throws PipelineNotFoundException when no configured pipeline has that id
     * @throws CiToolException           when the CI tool can't be reached or refuses
     */
    public Optional<String> startBuild(String pipelineId, Map<String, String> parameters) {
        return ci.triggerBuild(pipeline(pipelineId).job(), parameters);
    }

    private CiPipeline pipeline(String pipelineId) {
        return properties.pipelines().stream()
                .filter(pipeline -> pipeline.id().equals(pipelineId))
                .findFirst()
                .orElseThrow(() -> new PipelineNotFoundException(pipelineId));
    }
}
