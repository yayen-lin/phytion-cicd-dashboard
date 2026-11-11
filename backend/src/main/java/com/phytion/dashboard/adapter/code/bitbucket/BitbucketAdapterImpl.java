package com.phytion.dashboard.adapter.code.bitbucket;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.phytion.dashboard.adapter.code.CodeToolException;
import com.phytion.dashboard.model.CodeBranch;
import com.phytion.dashboard.model.CodeCommit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class BitbucketAdapterImpl implements BitbucketAdapter {

    private final RestClient client;
    private final BitbucketProperties properties;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    public BitbucketAdapterImpl(BitbucketProperties properties) {
        this(RestClient.builder().baseUrl(properties.url()).build(), properties);
    }

    // constructor for test
    BitbucketAdapterImpl(RestClient client, BitbucketProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public List<CodeBranch> listBranches(String workspace, String name) {
        return call(() -> {
            BitbucketJson.Page<BitbucketJson.Branch> page = client.get()
                    // sort=-target.date: the branch with the newest commit first
                    .uri("/repositories/{workspace}/{repo}/refs/branches?sort=-target.date&pagelen={max}",
                            workspace, name, properties.maxBranches())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return values(page).stream()
                    .map(branch -> new CodeBranch(branch.name(), toCommit(branch.target()), href(branch.links())))
                    .toList();
        });
    }

    @Override
    public List<CodeCommit> listCommits(String workspace, String name, String branch) {
        return call(() -> {
            BitbucketJson.Page<BitbucketJson.Commit> page = client.get()
                    .uri("/repositories/{workspace}/{repo}/commits?include={branch}&pagelen={max}",
                            workspace, name, branch, properties.maxCommits())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return values(page).stream().map(BitbucketAdapterImpl::toCommit).toList();
        });
    }

    @Override
    public String getCommitUrl(String workspace, String name, String commitHash) {
        return UriComponentsBuilder.fromUriString(properties.webUrl())
                .pathSegment(workspace, name, "commits", commitHash)
                .encode()
                .toUriString();
    }

    @Override
    public void createTag(String workspace, String name, String token, String tag, String commitHash) {
        call(() -> {
            try {
                client.post()
                        .uri("/repositories/{workspace}/{repo}/refs/tags", workspace, name)
                        .headers(headers -> headers.setBearerAuth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new BitbucketJson.NewTag(tag, new BitbucketJson.NewTagTarget(commitHash)))
                        .retrieve()
                        .toBodilessEntity();
                return null;
            } catch (HttpClientErrorException e) {
                throw new CodeToolException(tagRefused(workspace + "/" + name, e), e);
            }
        });
    }

    /**
     * Why Bitbucket refused to create a tag
     */
    private static String tagRefused(String repo, HttpClientErrorException e) {
        return switch (e.getStatusCode().value()) {
            // e.g. the tag already exists, or there's no such commit
            case 400 -> "Bitbucket refused the tag: " + errorReason(e);
            case 401 -> "Bitbucket rejected the token for " + repo + " (invalid or expired)";
            case 403 -> "The token for " + repo + " may not create tags (it needs Repositories: Write)";
            // What Bitbucket answers for another repo's token, rather than 403
            case 404 -> "Bitbucket didn't find " + repo + " with its token (is it that repo's token?)";
            default -> "Bitbucket answered " + e.getStatusCode().value() + " " + e.getStatusText();
        };
    }

    /**
     * Bitbucket's error message with what was wrong with which field
     */
    private static String errorReason(HttpClientErrorException e) {
        BitbucketJson.ErrorDetails error;
        try {
            BitbucketJson.ErrorResponse response =
                    JSON.readValue(e.getResponseBodyAsString(), BitbucketJson.ErrorResponse.class);
            error = response == null ? null : response.error();
        } catch (JacksonException notJson) {
            error = null;
        }
        if (error == null || error.message() == null) {
            return e.getStatusCode().value() + " " + e.getStatusText();
        }
        if (error.fields() == null || error.fields().isEmpty()) {
            return error.message();
        }
        return error.fields().entrySet().stream()
                .map(field -> field.getKey() + ": " + field.getValue())
                .collect(Collectors.joining(", ", error.message() + " (", ")"));
    }

    /**
     * Runs a request to Bitbucket, turning HTTP client errors into a CodeToolException with a short message.
     */
    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpStatusCodeException e) {
            throw new CodeToolException("Bitbucket answered " + e.getStatusCode().value() + " " + e.getStatusText(), e);
        } catch (ResourceAccessException e) {
            throw new CodeToolException("Bitbucket not reachable", e);
        } catch (RestClientException e) {
            throw new CodeToolException("Bitbucket request failed", e);
        }
    }

    private static <T> List<T> values(BitbucketJson.Page<T> page) {
        return page == null || page.values() == null ? List.of() : page.values();
    }

    private static CodeCommit toCommit(BitbucketJson.Commit commit) {
        if (commit == null) {
            return null;
        }
        return new CodeCommit(
                commit.hash(),
                commit.message(),
                author(commit.author()),
                commit.date() == null ? null : commit.date().toInstant(),
                href(commit.links()));
    }

    /**
     * The Bitbucket user's name, else the git author without the email
     */
    private static String author(BitbucketJson.Author author) {
        if (author == null) {
            return null;
        }
        if (author.user() != null && author.user().displayName() != null) {
            return author.user().displayName();
        }
        return author.raw() == null ? null : author.raw().replaceFirst("\\s*<[^>]*>\\s*$", "");
    }

    private static String href(BitbucketJson.Links links) {
        return links == null || links.html() == null ? null : links.html().href();
    }
}
