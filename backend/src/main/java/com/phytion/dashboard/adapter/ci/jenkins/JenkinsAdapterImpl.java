package com.phytion.dashboard.adapter.ci.jenkins;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.model.CiBuildDetails;
import com.phytion.dashboard.model.CiTestCase;
import com.phytion.dashboard.model.CiTestCounts;
import com.phytion.dashboard.model.CiTestReport;
import com.phytion.dashboard.model.CiBuild;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * CI adapter for Jenkins
 */
@Component
public class JenkinsAdapterImpl implements JenkinsAdapter {

    private final RestClient jenkins;
    private final JenkinsProperties properties;

    @Autowired
    public JenkinsAdapterImpl(JenkinsProperties properties) {
        this(RestClient.builder()
                        .baseUrl(properties.url())
                        .defaultHeaders(headers -> headers.setBasicAuth(properties.user(), properties.token()))
                        .build(),
                properties);
    }

    // For tests, which pass a RestClient wired to a fake Jenkins
    JenkinsAdapterImpl(RestClient jenkins, JenkinsProperties properties) {
        this.jenkins = jenkins;
        this.properties = properties;
    }

    @Override
    public List<CiBuild> listBuilds(String job) {
        return call(() -> {
            // e.g. builds[number,result,...]{0,20}: the newest maxBuilds builds
            String tree = "builds[" + JenkinsJson.BUILD_FIELDS + "]{0," + properties.maxBuilds() + "}";
            JenkinsJson.Job response = jenkins.get()
                    .uri("/job/{job}/api/json?tree={tree}", job, tree)
                    .retrieve()
                    .body(JenkinsJson.Job.class);
            if (response == null || response.builds() == null) {
                return List.of();
            }
            return response.builds().stream().map(JenkinsAdapterImpl::toCiBuild).toList();
        });
    }

    @Override
    public Optional<CiBuildDetails> getBuild(String job, String buildId) {
        return call(() -> {
            try {
                JenkinsJson.Build build = jenkins.get()
                        .uri("/job/{job}/{id}/api/json?tree={tree}", job, buildId, JenkinsJson.BUILD_DETAIL_FIELDS)
                        .retrieve()
                        .body(JenkinsJson.Build.class);
                return Optional.ofNullable(build).map(b -> new CiBuildDetails(toCiBuild(b), startedBy(b)));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            }
        });
    }

    @Override
    public Optional<CiTestReport> getTestReport(String job, String buildId) {
        return call(() -> {
            try {
                JenkinsJson.TestReport report = jenkins.get()
                        .uri("/job/{job}/{id}/testReport/api/json?tree={tree}", job, buildId,
                                JenkinsJson.TEST_REPORT_FIELDS)
                        .retrieve()
                        .body(JenkinsJson.TestReport.class);
                return Optional.ofNullable(report).map(JenkinsAdapterImpl::toTestReport);
            } catch (HttpClientErrorException.NotFound e) {
                // No such build, or it never published test results
                return Optional.empty();
            }
        });
    }

    /**
     * Jenkins doesn't start a build right away: it puts it in a queue and answers with the queue item's URL.
     * The build (and its number) exists once a worker picks the item up, so we ask the queue item until then.
     */
    @Override
    public Optional<String> triggerBuild(String job, Map<String, String> parameters) {
        return call(() -> {
            // buildWithParameters, not build: the jobs take a parameter (GIT_REF), and Jenkins uses its default (main)
            // when it isn't given. The parameters go in a form body, so values like refs/tags/v1.4.0 need no escaping.
            // delay=0sec skips Jenkins' default 5 second "quiet period" before a queued build may start
            RestClient.RequestBodySpec request = jenkins.post()
                    .uri("/job/{job}/buildWithParameters?delay=0sec", job);
            if (!parameters.isEmpty()) {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                parameters.forEach(form::add);
                request.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form);
            }
            URI queueItem = request
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders()
                    .getLocation();
            if (queueItem == null) {
                throw new CiToolException("Jenkins did not say where it queued the build");
            }
            // Use only the path: the Location host is Jenkins' own idea of its URL, which may differ from ours
            String queueItemPath = queueItem.getPath().endsWith("/") ? queueItem.getPath() : queueItem.getPath() + "/";

            Instant giveUpAt = Instant.now().plus(properties.queueTimeout());
            while (true) {
                JenkinsJson.QueueItem item = jenkins.get()
                        .uri(queueItemPath + "api/json")
                        .retrieve()
                        .body(JenkinsJson.QueueItem.class);
                if (item != null && item.cancelled()) {
                    throw new CiToolException("Jenkins cancelled the queued build");
                }
                if (item != null && item.executable() != null) {
                    return Optional.of(String.valueOf(item.executable().number()));
                }
                if (Instant.now().isAfter(giveUpAt)) {
                    return Optional.empty();
                }
                sleep(properties.queuePollInterval());
            }
        });
    }

    /**
     * Runs a request to Jenkins, turning HTTP client errors into a CiToolException with a short message.
     */
    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpStatusCodeException e) {
            // Keep just the status: the body is often a whole HTML error page
            throw new CiToolException("Jenkins answered " + e.getStatusCode().value() + " " + e.getStatusText(), e);
        } catch (ResourceAccessException e) {
            throw new CiToolException("Jenkins not reachable", e);
        } catch (RestClientException e) {
            throw new CiToolException("Jenkins request failed", e);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CiToolException("Interrupted while waiting for Jenkins to start the build", e);
        }
    }

    private static CiBuild toCiBuild(JenkinsJson.Build build) {
        JenkinsJson.Revision revision = build.actions() == null ? null : build.actions().stream()
                .map(JenkinsJson.Action::lastBuiltRevision)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        String commit = revision == null ? null : revision.sha1();
        String branch = revision == null || revision.branch() == null || revision.branch().isEmpty()
                ? null
                // "refs/remotes/origin/main" -> "main"
                : revision.branch().getFirst().name().replaceFirst("^refs/remotes/[^/]+/", "");

        return new CiBuild(
                String.valueOf(build.number()),
                toStatus(build),
                commit,
                branch,
                Instant.ofEpochMilli(build.timestamp()),
                build.inProgress() ? null : build.duration(),
                build.url(),
                testCounts(build));
    }

    /** The counts from the build's TestResultAction, or null when it has none */
    private static CiTestCounts testCounts(JenkinsJson.Build build) {
        if (build.actions() == null) {
            return null;
        }
        return build.actions().stream()
                .filter(action -> action.totalCount() != null)
                .findFirst()
                .map(action -> {
                    int failed = action.failCount() == null ? 0 : action.failCount();
                    int skipped = action.skipCount() == null ? 0 : action.skipCount();
                    return new CiTestCounts(action.totalCount() - failed - skipped, failed, skipped);
                })
                .orElse(null);
    }

    private static CiTestReport toTestReport(JenkinsJson.TestReport report) {
        List<CiTestCase> cases = report.suites() == null ? List.of() : report.suites().stream()
                .filter(suite -> suite.cases() != null)
                .flatMap(suite -> suite.cases().stream())
                .map(testCase -> {
                    CiTestCase.Status status = toTestStatus(testCase.status());
                    return new CiTestCase(
                            testCase.className(),
                            testCase.name(),
                            status,
                            secondsToMillis(testCase.duration()),
                            status == CiTestCase.Status.FAILED ? failureMessage(testCase) : null);
                })
                .toList();
        return new CiTestReport(
                new CiTestCounts(report.passCount(), report.failCount(), report.skipCount()),
                secondsToMillis(report.duration()),
                cases);
    }

    /** The failure's message, or its stack trace when it has no message (e.g. a bare assertTrue) */
    private static String failureMessage(JenkinsJson.TestCase testCase) {
        String details = testCase.errorDetails();
        return details == null || details.isBlank() ? testCase.errorStackTrace() : details;
    }

    private static CiTestCase.Status toTestStatus(String status) {
        if (status == null) {
            return CiTestCase.Status.PASSED;
        }
        return switch (status) {
            // FIXED: passes now, failed in the build before
            case "PASSED", "FIXED" -> CiTestCase.Status.PASSED;
            case "SKIPPED" -> CiTestCase.Status.SKIPPED;
            // REGRESSION: fails now, passed in the build before
            default -> CiTestCase.Status.FAILED;
        };
    }

    private static long secondsToMillis(double seconds) {
        return Math.round(seconds * 1000);
    }

    /** The Jenkins user who started the build, or null when no user did (e.g. a push or a timer) */
    private static String startedBy(JenkinsJson.Build build) {
        if (build.actions() == null) {
            return null;
        }
        return build.actions().stream()
                .filter(action -> action.causes() != null)
                .flatMap(action -> action.causes().stream())
                .map(cause -> cause.userName() != null ? cause.userName() : cause.userId())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static CiBuild.Status toStatus(JenkinsJson.Build build) {
        if (build.inProgress()) {
            return CiBuild.Status.RUNNING;
        }
        if (build.result() == null) {
            return CiBuild.Status.UNKNOWN;
        }
        return switch (build.result()) {
            case "SUCCESS" -> CiBuild.Status.SUCCESS;
            case "FAILURE" -> CiBuild.Status.FAILED;
            case "UNSTABLE" -> CiBuild.Status.UNSTABLE;
            case "ABORTED" -> CiBuild.Status.ABORTED;
            default -> CiBuild.Status.UNKNOWN;
        };
    }
}
