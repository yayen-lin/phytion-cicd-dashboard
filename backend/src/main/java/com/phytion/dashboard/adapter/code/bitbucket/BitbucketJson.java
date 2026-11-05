package com.phytion.dashboard.adapter.code.bitbucket;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bitbucket's JSON API responses that BitbucketAdapter reads. Everything else is ignored.
 */
final class BitbucketJson {

    private BitbucketJson() {
    }

    /** One page of a list, e.g. branches or commits. "next" (the next page's URL) is ignored: we only read page one. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Page<T>(List<T> values) {
    }

    /** Each entry of GET /repositories/{workspace}/{repo}/refs/branches. "target" is the branch's latest commit. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Branch(String name, Commit target, Links links) {
    }

    /** Each entry of GET /repositories/{workspace}/{repo}/commits */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Commit(String hash, OffsetDateTime date, String message, Author author, Links links) {
    }

    /** "raw" is the git author, e.g. "Andy Lin <andy@example.com>"; "user" is set only when it matches a Bitbucket account */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Author(String raw, User user) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record User(@JsonProperty("display_name") String displayName) {
    }

    /** "html" is the page in Bitbucket's web UI */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Links(Link html) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Link(String href) {
    }
}
