package com.phytion.dashboard.adapter.ci.jenkins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.model.CiBuildDetails;
import com.phytion.dashboard.model.CiTestCase;
import com.phytion.dashboard.model.CiTestCounts;
import com.phytion.dashboard.model.CiTestReport;
import com.phytion.dashboard.model.CiBuild;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Tests the Jenkins adapter against a fake Jenkins, using JSON shaped like real Jenkins responses.
 */
class JenkinsAdapterImplTests {

    private RestClient.Builder builder;
    private MockRestServiceServer jenkins;
    private JenkinsAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("http://jenkins");
        jenkins = MockRestServiceServer.bindTo(builder).build();
        // Don't wait between queue checks, and give up after the first check that finds the build still queued
        adapter = new JenkinsAdapterImpl(builder.build(), properties(Duration.ZERO));
    }

    @Test
    void listBuildsMapsJenkinsBuilds() {
        jenkins.expect(requestTo(startsWith("http://jenkins/job/sample-app/api/json")))
                .andRespond(withSuccess("""
                        {"_class": "org.jenkinsci.plugins.workflow.job.WorkflowJob", "builds": [
                          {"_class": "org.jenkinsci.plugins.workflow.job.WorkflowRun",
                           "number": 3, "result": null, "inProgress": true, "timestamp": 1793745100000,
                           "duration": 0, "url": "http://jenkins/job/sample-app/3/", "actions": [{}]},
                          {"_class": "org.jenkinsci.plugins.workflow.job.WorkflowRun",
                           "number": 2, "result": "SUCCESS", "inProgress": false, "timestamp": 1793745018832,
                           "duration": 5516, "url": "http://jenkins/job/sample-app/2/",
                           "actions": [{"_class": "hudson.model.CauseAction"}, {},
                             {"_class": "hudson.plugins.git.util.BuildData", "lastBuiltRevision": {
                               "SHA1": "cc99f6b852396cb487275a77c5f008e6077bdd0b",
                               "branch": [{"name": "refs/remotes/origin/main"}]}},
                             {"_class": "hudson.tasks.junit.TestResultAction", "totalCount": 6, "failCount": 1,
                              "skipCount": 2, "urlName": "testReport"}]},
                          {"_class": "org.jenkinsci.plugins.workflow.job.WorkflowRun",
                           "number": 1, "result": "FAILURE", "inProgress": false, "timestamp": 1793744856847,
                           "duration": 43745, "url": "http://jenkins/job/sample-app/1/", "actions": []}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<CiBuild> builds = adapter.listBuilds("sample-app");

        assertThat(builds).containsExactly(
                new CiBuild("3", CiBuild.Status.RUNNING, null, null, Instant.ofEpochMilli(1793745100000L), null,
                        "http://jenkins/job/sample-app/3/", null),
                // totalCount includes the failed and skipped tests
                new CiBuild("2", CiBuild.Status.SUCCESS, "cc99f6b852396cb487275a77c5f008e6077bdd0b", "main",
                        Instant.ofEpochMilli(1793745018832L), 5516L, "http://jenkins/job/sample-app/2/",
                        new CiTestCounts(3, 1, 2)),
                new CiBuild("1", CiBuild.Status.FAILED, null, null, Instant.ofEpochMilli(1793744856847L), 43745L,
                        "http://jenkins/job/sample-app/1/", null));
        jenkins.verify();
    }

    @Test
    void listBuildsOfJobWithoutBuildsIsEmpty() {
        jenkins.expect(requestTo(startsWith("http://jenkins/job/sample-app/api/json")))
                .andRespond(withSuccess("{\"builds\": []}", MediaType.APPLICATION_JSON));

        assertThat(adapter.listBuilds("sample-app")).isEmpty();
    }

    @Test
    void getBuildReturnsOneBuild() {
        jenkins.expect(requestTo(startsWith("http://jenkins/job/sample-app/2/api/json")))
                .andRespond(withSuccess("""
                        {"number": 2, "result": "ABORTED", "inProgress": false, "timestamp": 1793745018832,
                         "duration": 1000, "url": "http://jenkins/job/sample-app/2/"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.getBuild("sample-app", "2"))
                .map(details -> details.build().status())
                .contains(CiBuild.Status.ABORTED);
    }

    @Test
    void getBuildReadsTheJenkinsUserWhoStartedIt() {
        jenkins.expect(requestTo(startsWith("http://jenkins/job/sample-app/5/api/json")))
                .andRespond(withSuccess("""
                        {"number": 5, "result": "SUCCESS", "inProgress": false, "timestamp": 1793745018832,
                         "duration": 1000, "url": "http://jenkins/job/sample-app/5/", "actions": [
                           {"_class": "hudson.model.CauseAction", "causes": [
                             {"_class": "hudson.model.Cause$UserIdCause", "userId": "andy", "userName": "Andy Lin"}]},
                           {}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.getBuild("sample-app", "5")).map(CiBuildDetails::startedBy).contains("Andy Lin");
    }

    @Test
    void getBuildStartedByAPushHasNoUser() {
        jenkins.expect(requestTo(startsWith("http://jenkins/job/sample-app/6/api/json")))
                .andRespond(withSuccess("""
                        {"number": 6, "result": "SUCCESS", "inProgress": false, "timestamp": 1793745018832,
                         "duration": 1000, "url": "http://jenkins/job/sample-app/6/", "actions": [
                           {"_class": "hudson.model.CauseAction", "causes": [
                             {"_class": "hudson.triggers.SCMTrigger$SCMTriggerCause"}]}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.getBuild("sample-app", "6")).isPresent().get()
                .extracting(CiBuildDetails::startedBy).isNull();
    }

    @Test
    void getBuildOfMissingBuildIsEmpty() {
        jenkins.expect(requestTo(startsWith("http://jenkins/job/sample-app/99/api/json")))
                .andRespond(withResourceNotFound());

        assertThat(adapter.getBuild("sample-app", "99")).isEmpty();
    }

    @Test
    void getTestReportMapsEveryTestCase() {
        jenkins.expect(requestTo(startsWith("http://jenkins/job/dummy-app/4/testReport/api/json")))
                .andRespond(withSuccess("""
                        {"_class": "hudson.tasks.junit.TestResult", "duration": 0.941, "passCount": 2,
                         "failCount": 2, "skipCount": 1, "suites": [
                          {"name": "com.phytion.dummyapp.CreditCalculatorTests", "duration": 0.025, "cases": [
                            {"className": "com.phytion.dummyapp.CreditCalculatorTests", "name": "noMethaneIsNoCredits()",
                             "status": "PASSED", "duration": 0.014, "errorDetails": null},
                            {"className": "com.phytion.dummyapp.CreditCalculatorTests", "name": "totalCredits()",
                             "status": "REGRESSION", "duration": 0.002,
                             "errorDetails": "expected: <42.0> but was: <41.0>",
                             "errorStackTrace": "org.opentest4j.AssertionFailedError: expected: <42.0> but was: <41.0>"},
                            {"className": "com.phytion.dummyapp.CreditCalculatorTests", "name": "negative()",
                             "status": "FIXED", "duration": 0.001, "errorDetails": null}]},
                          {"name": "com.phytion.dummyapp.CreditControllerTests", "duration": 0.0, "cases": [
                            {"className": "com.phytion.dummyapp.CreditControllerTests", "name": "credits()",
                             "status": "FAILED", "duration": 0.0, "errorDetails": null,
                             "errorStackTrace": "java.lang.IllegalStateException\\n\\tat CreditController.credits"},
                            {"className": "com.phytion.dummyapp.CreditControllerTests", "name": "later()",
                             "status": "SKIPPED", "duration": 0.0, "errorDetails": null}]}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.getTestReport("dummy-app", "4")).contains(new CiTestReport(
                new CiTestCounts(2, 2, 1),
                941L,
                List.of(
                        new CiTestCase("com.phytion.dummyapp.CreditCalculatorTests", "noMethaneIsNoCredits()",
                                CiTestCase.Status.PASSED, 14L, null),
                        new CiTestCase("com.phytion.dummyapp.CreditCalculatorTests", "totalCredits()",
                                CiTestCase.Status.FAILED, 2L, "expected: <42.0> but was: <41.0>"),
                        new CiTestCase("com.phytion.dummyapp.CreditCalculatorTests", "negative()",
                                CiTestCase.Status.PASSED, 1L, null),
                        new CiTestCase("com.phytion.dummyapp.CreditControllerTests", "credits()",
                                // No message: the stack trace instead
                                CiTestCase.Status.FAILED, 0L,
                                "java.lang.IllegalStateException\n\tat CreditController.credits"),
                        new CiTestCase("com.phytion.dummyapp.CreditControllerTests", "later()",
                                CiTestCase.Status.SKIPPED, 0L, null))));
    }

    @Test
    void getTestReportOfBuildWithoutTestsIsEmpty() {
        jenkins.expect(requestTo(startsWith("http://jenkins/job/dummy-app/5/testReport/api/json")))
                .andRespond(withResourceNotFound());

        assertThat(adapter.getTestReport("dummy-app", "5")).isEmpty();
    }

    @Test
    void triggerBuildReturnsTheBuildNumberOnceTheQueuedBuildStarts() {
        expectBuildQueued();
        // First check: still waiting for a worker. Second check: started as build 4.
        expectQueueItem("{\"cancelled\": false, \"executable\": null}");
        expectQueueItem("{\"cancelled\": false, \"executable\": {\"number\": 4}}");
        JenkinsAdapterImpl adapterThatWaits = new JenkinsAdapterImpl(builder.build(), properties(Duration.ofSeconds(5)));

        assertThat(adapterThatWaits.triggerBuild("sample-app", Map.of())).contains("4");
        jenkins.verify();
    }

    @Test
    void triggerBuildWithoutParametersSendsNoBodySoJenkinsUsesTheDefaults() {
        jenkins.expect(requestTo("http://jenkins/job/sample-app/buildWithParameters?delay=0sec"))
                .andExpect(content().string(""))
                .andRespond(withCreatedEntity(URI.create("http://localhost:8081/queue/item/5/")));
        expectQueueItem("{\"cancelled\": false, \"executable\": {\"number\": 4}}");

        assertThat(adapter.triggerBuild("sample-app", Map.of())).contains("4");
        jenkins.verify();
    }

    @Test
    void triggerBuildSendsTheParametersAsAForm() {
        jenkins.expect(requestTo("http://jenkins/job/sample-app/buildWithParameters?delay=0sec"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(MultiValueMap.fromSingleValue(
                        Map.of("GIT_REF", "refs/tags/v1.4.0"))))
                .andRespond(withCreatedEntity(URI.create("http://localhost:8081/queue/item/5/")));
        expectQueueItem("{\"cancelled\": false, \"executable\": {\"number\": 12}}");

        assertThat(adapter.triggerBuild("sample-app", Map.of("GIT_REF", "refs/tags/v1.4.0"))).contains("12");
        jenkins.verify();
    }

    @Test
    void triggerBuildIsEmptyWhenTheBuildIsStillQueuedAtTheTimeout() {
        expectBuildQueued();
        expectQueueItem("{\"cancelled\": false, \"executable\": null}");

        assertThat(adapter.triggerBuild("sample-app", Map.of())).isEmpty();
        jenkins.verify();
    }

    @Test
    void triggerBuildFailsWhenJenkinsCancelsTheBuild() {
        expectBuildQueued();
        expectQueueItem("{\"cancelled\": true, \"executable\": null}");

        assertThatThrownBy(() -> adapter.triggerBuild("sample-app", Map.of()))
                .isInstanceOf(CiToolException.class)
                .hasMessage("Jenkins cancelled the queued build");
    }

    @Test
    void jenkinsErrorBecomesCiToolExceptionWithJustTheStatus() {
        jenkins.expect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("<html>long error page</html>"));

        assertThatThrownBy(() -> adapter.triggerBuild("sample-app", Map.of()))
                .isInstanceOf(CiToolException.class)
                .hasMessage("Jenkins answered 403 Forbidden");
    }

    /** Settings for tests: no wait between queue checks, and the given queue timeout */
    private static JenkinsProperties properties(Duration queueTimeout) {
        return new JenkinsProperties("http://jenkins", "user", "token", 20, Duration.ZERO, queueTimeout);
    }

    private void expectBuildQueued() {
        // Jenkins answers with its own URL for the queue item, which may not match the URL we use
        jenkins.expect(method(HttpMethod.POST))
                .andExpect(requestTo("http://jenkins/job/sample-app/buildWithParameters?delay=0sec"))
                .andRespond(withCreatedEntity(URI.create("http://localhost:8081/queue/item/5/")));
    }

    private void expectQueueItem(String json) {
        jenkins.expect(requestTo("http://jenkins/queue/item/5/api/json"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }
}
