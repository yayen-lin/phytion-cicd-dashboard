package com.phytion.dashboard.adapter.code.bitbucket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;

import com.phytion.dashboard.adapter.code.CodeToolException;
import com.phytion.dashboard.model.CodeBranch;
import com.phytion.dashboard.model.CodeCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests the Bitbucket adapter against a fake Bitbucket, using JSON shaped like real Bitbucket responses.
 */
class BitbucketAdapterImplTests {

    private static final String TAGS = "http://bitbucket/repositories/andy-test-app/sample-app/refs/tags";
    private static final String COMMIT = "a4dad009a1f3ef48f7239a3dd155c64ca51d466f";

    private MockRestServiceServer bitbucket;
    private BitbucketAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://bitbucket");
        bitbucket = MockRestServiceServer.bindTo(builder).build();
        adapter = new BitbucketAdapterImpl(builder.build(),
                new BitbucketProperties("http://bitbucket", "https://bitbucket.org", 50, 20));
    }

    @Test
    void listBranchesMapsBitbucketBranches() {
        bitbucket.expect(requestTo(
                        "http://bitbucket/repositories/andy-test-app/sample-app/refs/branches?sort=-target.date&pagelen=50"))
                .andRespond(withSuccess("""
                        {"pagelen": 50, "size": 1, "page": 1, "values": [
                          {"name": "main", "type": "branch",
                           "target": {"type": "commit", "hash": "cc99f6b852396cb487275a77c5f008e6077bdd0b",
                             "date": "2026-11-03T22:11:13+00:00", "message": "sample app initial commit\\n",
                             "author": {"type": "author", "raw": "yayen-lin <andy@example.com>",
                               "user": {"display_name": "Andy Lin", "type": "user"}},
                             "links": {"html": {"href": "https://bitbucket.org/andy-test-app/sample-app/commits/cc99f6b852396cb487275a77c5f008e6077bdd0b"}}},
                           "links": {"html": {"href": "https://bitbucket.org/andy-test-app/sample-app/branch/main"}}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.listBranches("andy-test-app", "sample-app")).containsExactly(new CodeBranch(
                "main",
                new CodeCommit("cc99f6b852396cb487275a77c5f008e6077bdd0b", "sample app initial commit\n",
                        "Andy Lin", Instant.parse("2026-11-03T22:11:13Z"),
                        "https://bitbucket.org/andy-test-app/sample-app/commits/cc99f6b852396cb487275a77c5f008e6077bdd0b"),
                "https://bitbucket.org/andy-test-app/sample-app/branch/main"));
        bitbucket.verify();
    }

    @Test
    void listCommitsAsksForTheBranchAndMapsCommits() {
        bitbucket.expect(requestTo(
                        "http://bitbucket/repositories/andy-test-app/sample-app/commits?include=feature%2Flogin&pagelen=20"))
                .andRespond(withSuccess("""
                        {"pagelen": 20, "values": [
                          {"type": "commit", "hash": "aaa111", "date": "2026-11-04T09:00:00+01:00",
                           "message": "Add login", "author": {"raw": "Someone Else <someone@example.com>"},
                           "links": {"html": {"href": "https://bitbucket.org/andy-test-app/sample-app/commits/aaa111"}}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.listCommits("andy-test-app", "sample-app", "feature/login")).containsExactly(
                // No Bitbucket account for this author: the git name without the email
                new CodeCommit("aaa111", "Add login", "Someone Else", Instant.parse("2026-11-04T08:00:00Z"),
                        "https://bitbucket.org/andy-test-app/sample-app/commits/aaa111"));
        bitbucket.verify();
    }

    @Test
    void emptyRepoHasNoBranches() {
        bitbucket.expect(requestTo(
                        "http://bitbucket/repositories/andy-test-app/sample-app/refs/branches?sort=-target.date&pagelen=50"))
                .andRespond(withSuccess("{\"values\": []}", MediaType.APPLICATION_JSON));

        assertThat(adapter.listBranches("andy-test-app", "sample-app")).isEmpty();
    }

    @Test
    void bitbucketErrorBecomesCodeToolExceptionWithJustTheStatus() {
        bitbucket.expect(requestTo(
                        "http://bitbucket/repositories/andy-test-app/gone/refs/branches?sort=-target.date&pagelen=50"))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> adapter.listBranches("andy-test-app", "gone"))
                .isInstanceOf(CodeToolException.class)
                .hasMessage("Bitbucket answered 404 Not Found");
    }

    @Test
    void getCommitUrlIsTheCommitsPageOnTheWebsiteNotTheApi() {
        // The same address as the commit's links.html.href in Bitbucket's answers
        assertThat(adapter.getCommitUrl("andy-test-app", "sample-app", COMMIT))
                .isEqualTo("https://bitbucket.org/andy-test-app/sample-app/commits/" + COMMIT);
        // Building it doesn't ask Bitbucket
        bitbucket.verify();
    }

    @Test
    void readsSendNoToken() {
        bitbucket.expect(requestTo(
                        "http://bitbucket/repositories/andy-test-app/sample-app/refs/branches?sort=-target.date&pagelen=50"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("{\"values\": []}", MediaType.APPLICATION_JSON));

        adapter.listBranches("andy-test-app", "sample-app");
        bitbucket.verify();
    }

    @Test
    void createTagPostsTheTagAndCommitWithTheToken() {
        bitbucket.expect(requestTo(TAGS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer repo-token"))
                .andExpect(content().json("{\"name\": \"v1.4.0\", \"target\": {\"hash\": \"" + COMMIT + "\"}}", true))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\": \"v1.4.0\", \"type\": \"tag\", \"target\": {\"hash\": \"" + COMMIT + "\"}}"));

        adapter.createTag("andy-test-app", "sample-app", "repo-token", "v1.4.0", COMMIT);
        bitbucket.verify();
    }

    @Test
    void existingTagGivesBitbucketsReason() {
        expectTagRefused(HttpStatus.BAD_REQUEST, """
                {"type": "error", "error": {"message": "Tag \\"v0.1.1\\" already exists", "code": "INVALID_TAG_NAME",
                 "data": {"key": "INVALID_TAG_NAME"}}, "data": {"key": "INVALID_TAG_NAME"}}
                """);

        assertThatThrownBy(() -> adapter.createTag("andy-test-app", "sample-app", "repo-token", "v0.1.1", COMMIT))
                .isInstanceOf(CodeToolException.class)
                .hasMessage("Bitbucket refused the tag: Tag \"v0.1.1\" already exists");
    }

    @Test
    void unknownCommitGivesTheFieldThatIsWrong() {
        expectTagRefused(HttpStatus.BAD_REQUEST, """
                {"type": "error", "error": {"message": "Bad request",
                 "fields": {"target.hash": "ambiguous or non-existent hash"}}}
                """);

        assertThatThrownBy(() -> adapter.createTag("andy-test-app", "sample-app", "repo-token", "v1.4.0", "0000000"))
                .isInstanceOf(CodeToolException.class)
                .hasMessage("Bitbucket refused the tag: Bad request (target.hash: ambiguous or non-existent hash)");
    }

    @Test
    void refusalWithoutBitbucketsErrorJsonGivesTheStatus() {
        expectTagRefused(HttpStatus.BAD_REQUEST, "<html>Bad request</html>");

        assertThatThrownBy(() -> adapter.createTag("andy-test-app", "sample-app", "repo-token", "v1.4.0", COMMIT))
                .isInstanceOf(CodeToolException.class)
                .hasMessage("Bitbucket refused the tag: 400 Bad Request");
    }

    @Test
    void rejectedTokenSaysSo() {
        expectTagRefused(HttpStatus.UNAUTHORIZED, """
                {"type": "error", "error": {"message": "Token is invalid, expired, or not supported for this endpoint."}}
                """);

        assertThatThrownBy(() -> adapter.createTag("andy-test-app", "sample-app", "expired", "v1.4.0", COMMIT))
                .isInstanceOf(CodeToolException.class)
                .hasMessage("Bitbucket rejected the token for andy-test-app/sample-app (invalid or expired)");
    }

    @Test
    void anotherReposTokenSaysSo() {
        // Bitbucket answers 404, not 403, for a token of another repo
        expectTagRefused(HttpStatus.NOT_FOUND,
                "{\"type\": \"error\", \"error\": {\"message\": \"You may not have access to this repository\"}}");

        assertThatThrownBy(() -> adapter.createTag("andy-test-app", "sample-app", "dummy-app-token", "v1.4.0", COMMIT))
                .isInstanceOf(CodeToolException.class)
                .hasMessage("Bitbucket didn't find andy-test-app/sample-app with its token (is it that repo's token?)");
    }

    @Test
    void bitbucketDownWhileTaggingGivesJustTheStatus() {
        bitbucket.expect(requestTo(TAGS)).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.createTag("andy-test-app", "sample-app", "repo-token", "v1.4.0", COMMIT))
                .isInstanceOf(CodeToolException.class)
                .hasMessage("Bitbucket answered 500 Internal Server Error");
    }

    private void expectTagRefused(HttpStatus status, String body) {
        bitbucket.expect(requestTo(TAGS))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
    }
}
