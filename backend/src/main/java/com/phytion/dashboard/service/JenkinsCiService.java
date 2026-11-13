package com.phytion.dashboard.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.adapter.ci.jenkins.JenkinsAdapter;
import com.phytion.dashboard.model.App;
import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.CiBuildDetails;
import com.phytion.dashboard.model.CiPipeline;
import com.phytion.dashboard.model.CiTestReport;
import com.phytion.dashboard.model.CiBuild;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.phytion.dashboard.model.CiBuildDetails.DASHBOARD;

/**
 * CI builds of the apps' pipelines.
 */
@Service
@RequiredArgsConstructor
public class JenkinsCiService {

    private final JenkinsAdapter ci;
    private final AuditService audit;
    private final AppService apps;

    /**
     * Lists out the builds of an app's pipeline.
     *
     * @param appId the name of the app
     * @throws AppNotFoundException when no configured app has that id
     */
    public List<CiBuild> listBuilds(String appId) {
        return ci.listBuilds(pipeline(appId).job());
    }

    /**
     * One build of an app's pipeline.
     *
     * @param appId the name of the app
     * @return the build, or empty when the CI tool has no such build
     * @throws AppNotFoundException when no configured app has that id
     */
    public Optional<CiBuildDetails> getBuild(String appId, String buildId) {
        App app = apps.getApp(appId);
        CiPipeline pipeline = app.pipeline();
        return ci.getBuild(pipeline.job(), buildId).map(details -> audit.findBuildStarter(app.id(), buildId)
                .map(username -> new CiBuildDetails(details.build(), username, DASHBOARD))
                .orElseGet(() -> new CiBuildDetails(details.build(), details.startedBy(),
                        details.startedBy() == null ? null : pipeline.tool())));
    }

    /**
     * @param appId the name of the app
     * @return the build's test results, or empty when it has none (or doesn't exist)
     * @throws AppNotFoundException when no configured app has that id
     */
    public Optional<CiTestReport> getTestReport(String appId, String buildId) {
        return ci.getTestReport(pipeline(appId).job(), buildId);
    }

    /**
     * Starts a build of an app's pipeline and records it in the audit log.
     *
     * @param appId    the name of the app
     * @param username who started it
     * @return the new build's id, or empty when the CI tool hasn't started the build yet
     * @throws AppNotFoundException when no configured app has that id
     * @throws CiToolException      when the CI tool can't be reached or refuses
     */
    public Optional<String> triggerBuild(String appId, String username) {
        App app = apps.getApp(appId);
        try {
            Optional<String> buildId = ci.triggerBuild(app.pipeline().job(), Map.of());
            audit.add(username, AuditEntry.ActionType.BUILD, app.id(), AuditEntry.ActionResult.SUCCESS,
                    buildId.orElse(null), buildId.isPresent() ? null : "Queued, but the CI tool hadn't started it yet",
                    null, null);
            return buildId;
        } catch (CiToolException e) {
            audit.add(username, AuditEntry.ActionType.BUILD, app.id(), AuditEntry.ActionResult.FAILED,
                    null, e.getMessage(), null, null);
            throw e;
        }
    }

    /**
     * Starts a build of an app's pipeline with parameters.
     *
     * @param appId      the name of the app
     * @param parameters e.g. GIT_REF -> refs/tags/v1.4.0
     * @return the new build's id, or empty when the CI tool hasn't started the build yet
     * @throws AppNotFoundException when no configured app has that id
     * @throws CiToolException      when the CI tool can't be reached or refuses
     */
    public Optional<String> startBuild(String appId, Map<String, String> parameters) {
        return ci.triggerBuild(pipeline(appId).job(), parameters);
    }

    private CiPipeline pipeline(String appId) {
        return apps.getApp(appId).pipeline();
    }
}
