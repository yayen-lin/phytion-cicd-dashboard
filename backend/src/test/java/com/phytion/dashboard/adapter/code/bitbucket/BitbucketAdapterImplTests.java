package com.phytion.dashboard.adapter.code.bitbucket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;

import com.phytion.dashboard.adapter.code.CodeToolException;
import com.phytion.dashboard.model.CodeBranch;
import com.phytion.dashboard.model.CodeCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests the Bitbucket adapter against a fake Bitbucket, using JSON shaped like real Bitbucket responses.
 */
class BitbucketAdapterImplTests {

    private MockRestServiceServer bitbucket;
    private BitbucketAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://bitbucket");
        bitbucket = MockRestServiceServer.bindTo(builder).build();
        adapter = new BitbucketAdapterImpl(builder.build(), new BitbucketProperties("http://bitbucket", 50, 20));
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

        assertThat(adapter.listBranches("andy-test-app/sample-app")).containsExactly(new CodeBranch(
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

        assertThat(adapter.listCommits("andy-test-app/sample-app", "feature/login")).containsExactly(
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

        assertThat(adapter.listBranches("andy-test-app/sample-app")).isEmpty();
    }

    @Test
    void bitbucketErrorBecomesCodeToolExceptionWithJustTheStatus() {
        bitbucket.expect(requestTo(
                        "http://bitbucket/repositories/andy-test-app/gone/refs/branches?sort=-target.date&pagelen=50"))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> adapter.listBranches("andy-test-app/gone"))
                .isInstanceOf(CodeToolException.class)
                .hasMessage("Bitbucket answered 404 Not Found");
    }
}
