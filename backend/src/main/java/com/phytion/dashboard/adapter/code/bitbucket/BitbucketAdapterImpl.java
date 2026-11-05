package com.phytion.dashboard.adapter.code.bitbucket;

import java.util.List;
import java.util.function.Supplier;

import com.phytion.dashboard.adapter.code.CodeToolException;
import com.phytion.dashboard.model.CodeBranch;
import com.phytion.dashboard.model.CodeCommit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Code adapter for Bitbucket Cloud
 */
@Component
public class BitbucketAdapterImpl implements BitbucketAdapter {

    private final RestClient bitbucket;
    private final BitbucketProperties properties;

    @Autowired
    public BitbucketAdapterImpl(BitbucketProperties properties) {
        this(RestClient.builder().baseUrl(properties.url()).build(), properties);
    }

    // For tests, which pass a RestClient wired to a fake Bitbucket
    BitbucketAdapterImpl(RestClient bitbucket, BitbucketProperties properties) {
        this.bitbucket = bitbucket;
        this.properties = properties;
    }

    @Override
    public List<CodeBranch> listBranches(String repo) {
        String[] parts = split(repo);
        return call(() -> {
            BitbucketJson.Page<BitbucketJson.Branch> page = bitbucket.get()
                    // sort=-target.date: the branch with the newest commit first
                    .uri("/repositories/{workspace}/{repo}/refs/branches?sort=-target.date&pagelen={max}",
                            parts[0], parts[1], properties.maxBranches())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return values(page).stream()
                    .map(branch -> new CodeBranch(branch.name(), toCommit(branch.target()), href(branch.links())))
                    .toList();
        });
    }

    @Override
    public List<CodeCommit> listCommits(String repo, String branch) {
        String[] parts = split(repo);
        return call(() -> {
            BitbucketJson.Page<BitbucketJson.Commit> page = bitbucket.get()
                    // include={branch} instead of /commits/{branch} in the path: branch names may contain "/"
                    .uri("/repositories/{workspace}/{repo}/commits?include={branch}&pagelen={max}",
                            parts[0], parts[1], branch, properties.maxCommits())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return values(page).stream().map(BitbucketAdapterImpl::toCommit).toList();
        });
    }

    /** "andy-test-app/sample-app" -> ["andy-test-app", "sample-app"], as separate path parts so "/" isn't encoded */
    private static String[] split(String repo) {
        String[] parts = repo.split("/", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Repo must be workspace/repo, got: " + repo);
        }
        return parts;
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

    /** The Bitbucket user's name, else the git author without the email: "Andy Lin <andy@example.com>" -> "Andy Lin" */
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
