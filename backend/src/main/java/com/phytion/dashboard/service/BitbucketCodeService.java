package com.phytion.dashboard.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import com.phytion.dashboard.adapter.code.CodeToolException;
import com.phytion.dashboard.adapter.code.bitbucket.BitbucketAdapter;
import com.phytion.dashboard.model.CodeBranch;
import com.phytion.dashboard.model.CodeBranchCommits;
import com.phytion.dashboard.model.CodeCommit;
import com.phytion.dashboard.model.CodeRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.phytion.dashboard.service.ParallelCalls.await;

/**
 * Branches and commits of the apps' repos, and creating tags in them.
 */
@Service
@RequiredArgsConstructor
public class BitbucketCodeService {

    private final BitbucketAdapter code;
    private final AppService apps;
    private static final List<String> DEFAULT_BRANCHES = List.of("main", "master");

    /**
     * Lists out the branches of an app's repo.
     *
     * @param appId name of the app
     * @throws AppNotFoundException when no configured app has that id
     */
    public List<CodeBranch> listBranches(String appId) {
        CodeRepo repo = repo(appId);
        return code.listBranches(repo.workspace(), repo.name());
    }

    /**
     * The branches of an app's repo with the commits on one of them.
     *
     * @param appId         name of the app
     * @param wantedBranch  the branch to show, or null for the default one
     * @return the branches, the branch shown (null when the repo has none) and its commits
     * @throws AppNotFoundException when no configured app has that id
     * @throws CodeToolException    when the code tool can't be reached
     */
    public CodeBranchCommits listBranchCommits(String appId, String wantedBranch) {
        CodeRepo repo = repo(appId);
        String guess = wantedBranch != null ? wantedBranch : DEFAULT_BRANCHES.getFirst();

        // read the branches and the guessed branch's commits at the same time; the guess may not exist
        List<CodeBranch> branches;
        Optional<List<CodeCommit>> guessedCommits;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<Optional<List<CodeCommit>>> guessRead = CompletableFuture.supplyAsync(() -> {
                try {
                    return Optional.of(code.listCommits(repo.workspace(), repo.name(), guess));
                } catch (CodeToolException e) {
                    return Optional.empty();
                }
            }, executor);
            branches = code.listBranches(repo.workspace(), repo.name());
            guessedCommits = await(guessRead);
        }

        // pick the branch to show
        List<String> names = branches.stream().map(CodeBranch::name).toList();
        String picked = Stream.concat(Stream.of(wantedBranch), DEFAULT_BRANCHES.stream())
                .filter(Objects::nonNull)
                .filter(names::contains)
                .findFirst()
                .orElse(names.isEmpty() ? null : names.getFirst());
        if (picked == null) {
            return new CodeBranchCommits(branches, null, List.of());
        }

        // use the guessed commits when the guess was right, else read the picked branch's
        List<CodeCommit> commits = picked.equals(guess) && guessedCommits.isPresent()
                ? guessedCommits.get()
                : code.listCommits(repo.workspace(), repo.name(), picked);
        return new CodeBranchCommits(branches, picked, commits);
    }

    /**
     * The names of an app repo's tags, the newest commit's first.
     *
     * @param appId name of the app
     * @throws AppNotFoundException when no configured app has that id
     * @throws CodeToolException    when the code tool can't be reached
     */
    public List<String> listTags(String appId) {
        CodeRepo repo = repo(appId);
        return code.listTags(repo.workspace(), repo.name());
    }

    /**
     * The address of a commit's page.
     *
     * @param appId      name of the app
     * @param commitHash full commit hash
     * @throws AppNotFoundException when no configured app has that id
     */
    public String getCommitUrl(String appId, String commitHash) {
        CodeRepo repo = repo(appId);
        return code.getCommitUrl(repo.workspace(), repo.name(), commitHash);
    }

    /**
     * Creates a tag on a commit.
     *
     * @param appId      name of the app
     * @param tag        version tag
     * @param commitHash full commit hash
     * @throws AppNotFoundException when no configured app has that id
     * @throws CodeToolException    when the repo has no token, or the code tool refuses
     */
    public void createTag(String appId, String tag, String commitHash) {
        CodeRepo repo = repo(appId);
        if (!repo.hasToken()) {
            throw new CodeToolException("No token is set for " + appId + "'s repo (" + repo.workspace() + "/"
                    + repo.name() + "), so the dashboard can't create tags there");
        }
        code.createTag(repo.workspace(), repo.name(), repo.token(), tag, commitHash);
    }

    private CodeRepo repo(String appId) {
        return apps.getApp(appId).repo();
    }
}
