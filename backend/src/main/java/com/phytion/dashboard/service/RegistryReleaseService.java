package com.phytion.dashboard.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.adapter.code.CodeToolException;
import com.phytion.dashboard.adapter.release.registry.RegistryAdapter;
import com.phytion.dashboard.adapter.release.registry.RegistryProperties;
import com.phytion.dashboard.config.CiProperties;
import com.phytion.dashboard.config.CodeProperties;
import com.phytion.dashboard.config.ReleaseProperties;
import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.ReleaseApp;
import com.phytion.dashboard.model.ReleaseVersion;
import org.springframework.stereotype.Service;

/**
 * Released versions of the configured apps, and releasing a new one (recorded in the audit log).
 */
@Service
public class RegistryReleaseService {

    private static final String REGISTRY = "registry";

    // The labels the Release stage of each app's Jenkinsfile puts on the image
    private static final String LABEL_CREATED = "org.opencontainers.image.created";
    private static final String LABEL_COMMIT = "org.opencontainers.image.revision";
    private static final String LABEL_BUILD = "com.phytionassetmanagement.build-number";

    // The Release stage tags images with the version number of the git tag, e.g. v1.4.0 -> 1.4.0
    private static final Pattern VERSION_NUMBER = Pattern.compile("(\\d{1,9})\\.(\\d{1,9})\\.(\\d{1,9})");

    // What can be released: a git tag like v1.4.0 on a commit given by its full hash
    private static final Pattern RELEASE_TAG = Pattern.compile("v\\d{1,9}\\.\\d{1,9}\\.\\d{1,9}");
    private static final Pattern FULL_COMMIT = Pattern.compile("[0-9a-f]{40}");

    // The Jenkins job parameter that says what to build (see the Release stage in each app's Jenkinsfile)
    private static final String GIT_REF = "GIT_REF";

    // Highest version number first (1.10.0 above 1.9.0); tags in another format (e.g. "latest") after them, by name
    private static final Comparator<String> NEWEST_FIRST = Comparator
            .comparing(RegistryReleaseService::versionNumber, Comparator.nullsLast(VersionNumber.HIGHEST_FIRST))
            .thenComparing(Comparator.naturalOrder());

    private final RegistryAdapter release;
    private final BitbucketCodeService code;
    private final JenkinsCiService ci;
    private final AuditService audit;
    private final List<ReleaseApp> apps;
    private final int maxVersions;

    public RegistryReleaseService(RegistryAdapter release, BitbucketCodeService code, JenkinsCiService ci,
                                  AuditService audit, ReleaseProperties properties, CodeProperties codeProperties,
                                  CiProperties ciProperties, RegistryProperties registryProperties) {
        // Fail at startup rather than on the first request
        for (ReleaseApp app : properties.apps()) {
            // The registry is the only release tool with an adapter so far
            if (!REGISTRY.equals(app.tool())) {
                throw new IllegalStateException("No adapter for release tool '" + app.tool()
                        + "' (app " + app.id() + "); only 'registry' is supported");
            }
            if (codeProperties.repos().stream().noneMatch(repo -> repo.id().equals(app.repo()))) {
                throw new IllegalStateException("App " + app.id() + " has repo '" + app.repo()
                        + "', which isn't one of the code repos (dashboard.code.repos)");
            }
            // Otherwise a release would create the tag and only then fail to start the build
            if (ciProperties.pipelines().stream().noneMatch(pipeline -> pipeline.id().equals(app.pipeline()))) {
                throw new IllegalStateException("App " + app.id() + " has pipeline '" + app.pipeline()
                        + "', which isn't one of the CI pipelines (dashboard.ci.pipelines)");
            }
        }
        this.release = release;
        this.code = code;
        this.ci = ci;
        this.audit = audit;
        this.apps = properties.apps();
        this.maxVersions = registryProperties.maxVersions();
    }

    /** The configured apps, in config order. */
    public List<ReleaseApp> apps() {
        return apps;
    }

    /**
     * The newest versions of an app (at most maxVersions), highest version number first.
     *
     * @param appId e.g. "registry:sample-app"
     * @throws AppNotFoundException when no configured app has that id
     */
    public List<ReleaseVersion> listVersions(String appId) {
        ReleaseApp app = app(appId);
        // Sort and cut by the tags first: the labels cost extra requests per version
        return release.listTags(app.image()).stream()
                .sorted(NEWEST_FIRST)
                .limit(maxVersions)
                .map(tag -> toVersion(app, tag, release.getLabels(app.image(), tag)))
                .toList();
    }

    /**
     * Releases a commit of an app as a version: creates the git tag (e.g. v1.4.0) on the commit, then starts the build
     * of that tag, whose Release stage pushes the image (e.g. 1.4.0). Recorded in the audit log as RELEASE, whether it
     * worked or not.
     *
     * @param appId    e.g. "registry:sample-app"
     * @param tag      the version to release, e.g. "v1.4.0"
     * @param commit   the full hash of the commit to release
     * @param username who released it
     * @return the tag build's id, or empty when the CI tool hasn't started the build yet
     * @throws AppNotFoundException     when no configured app has that id
     * @throws InvalidReleaseException  when the app is missing, or the tag or the commit isn't in the right format
     *                                  (nothing is recorded)
     * @throws CodeToolException        when the code tool doesn't create the tag (e.g. it already exists)
     * @throws CiToolException          when the tag was created but the build didn't start
     */
    public Optional<String> release(String appId, String tag, String commit, String username) {
        if (appId == null) {
            throw new InvalidReleaseException("No app given");
        }
        ReleaseApp app = app(appId);
        if (tag == null || !RELEASE_TAG.matcher(tag).matches()) {
            throw new InvalidReleaseException("A version looks like v1.4.0, got: " + tag);
        }
        if (commit == null || !FULL_COMMIT.matcher(commit).matches()) {
            throw new InvalidReleaseException("A commit is given by its full 40-character hash, got: " + commit);
        }
        String what = tag + " on commit " + commit.substring(0, 7);

        try {
            code.createTag(app.repo(), tag, commit);
        } catch (CodeToolException e) {
            audit.add(username, AuditEntry.ActionType.RELEASE, app.id(), AuditEntry.ActionResult.FAILED, null,
                    what + ": " + e.getMessage());
            throw e;
        }

        Optional<String> buildId;
        try {
            buildId = ci.startBuild(app.pipeline(), Map.of(GIT_REF, "refs/tags/" + tag));
        } catch (CiToolException e) {
            // The tag stays: that version is used up, the next release takes the next one
            String message = "Tag " + tag + " was created, but its build didn't start: " + e.getMessage();
            audit.add(username, AuditEntry.ActionType.RELEASE, app.id(), AuditEntry.ActionResult.FAILED, null,
                    what + ": " + message);
            throw new CiToolException(message, e);
        }
        String details = buildId.isPresent() ? what : what + "; build queued, but the CI tool hadn't started it yet";
        audit.add(username, AuditEntry.ActionType.RELEASE, app.id(), AuditEntry.ActionResult.SUCCESS,
                buildId.orElse(null), details);
        return buildId;
    }

    private ReleaseVersion toVersion(ReleaseApp app, String tag, Map<String, String> labels) {
        String commit = labels.get(LABEL_COMMIT);
        return new ReleaseVersion(tag, labels.get(LABEL_BUILD), commit,
                commit == null ? null : code.getCommitUrl(app.repo(), commit), instant(labels.get(LABEL_CREATED)));
    }

    /** "2026-11-06T18:16:33Z" -> that instant; null when missing or not a date */
    private static Instant instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** "1.10.0" -> 1, 10, 0; null for a tag in another format */
    private static VersionNumber versionNumber(String tag) {
        Matcher matcher = VERSION_NUMBER.matcher(tag);
        return matcher.matches()
                ? new VersionNumber(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)))
                : null;
    }

    private record VersionNumber(int major, int minor, int patch) {

        static final Comparator<VersionNumber> HIGHEST_FIRST = Comparator.comparingInt(VersionNumber::major)
                .thenComparingInt(VersionNumber::minor)
                .thenComparingInt(VersionNumber::patch)
                .reversed();
    }

    private ReleaseApp app(String appId) {
        return apps.stream()
                .filter(app -> app.id().equals(appId))
                .findFirst()
                .orElseThrow(() -> new AppNotFoundException(appId));
    }
}
