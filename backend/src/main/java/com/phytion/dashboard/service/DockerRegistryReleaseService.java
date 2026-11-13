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
import com.phytion.dashboard.adapter.release.dockerregistry.DockerRegistryAdapter;
import com.phytion.dashboard.adapter.release.dockerregistry.DockerRegistryProperties;
import com.phytion.dashboard.model.App;
import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.ReleaseImage;
import com.phytion.dashboard.model.ReleaseVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Released versions of the apps, and releasing a new one (recorded in the audit log).
 */
@Service
@RequiredArgsConstructor
public class DockerRegistryReleaseService {

    private final DockerRegistryAdapter release;
    private final BitbucketCodeService code;
    private final JenkinsCiService ci;
    private final AuditService audit;
    private final AppService apps;
    private final DockerRegistryProperties registryProperties;

    // The labels the Release stage of each app's Jenkinsfile puts on the image
    private static final String LABEL_CREATED = "org.opencontainers.image.created";
    private static final String LABEL_COMMIT = "org.opencontainers.image.revision";
    private static final String LABEL_BUILD = "com.phytionassetmanagement.build-number";

    // The Release stage tags images with the version number of the git tag, e.g. v1.4.0 -> 1.4.0
    private static final Pattern VERSION_NUMBER = Pattern.compile("(\\d{1,9})\\.(\\d{1,9})\\.(\\d{1,9})");

    // What can be released: a git tag like v1.4.0 on a commit given by its full hash
    private static final Pattern RELEASE_TAG = Pattern.compile("v\\d{1,9}\\.\\d{1,9}\\.\\d{1,9}");
    private static final Pattern FULL_COMMIT = Pattern.compile("[0-9a-f]{40}");

    // jenkins job parameter
    private static final String GIT_REF = "GIT_REF";

    private static final Comparator<String> NEWEST_FIRST = Comparator
            .comparing(DockerRegistryReleaseService::parseVersionString, Comparator.nullsLast(VersionNumber.HIGHEST_FIRST))
            .thenComparing(Comparator.naturalOrder());

    /**
     * The newest versions of an app (at most maxVersions), highest version number first.
     *
     * @param appId the name of the repo
     * @throws AppNotFoundException when no configured app has that id, or the app isn't released
     */
    public List<ReleaseVersion> listVersions(String appId) {
        ReleaseImage image = getReleaseImage(appId);
        // Sort and cut by the tags first: the labels cost extra requests per version
        return release.listTags(image.image()).stream()
                .sorted(NEWEST_FIRST)
                .limit(registryProperties.maxVersions())
                .map(tag -> toVersion(appId, tag, release.getLabels(image.image(), tag)))
                .toList();
    }

    /**
     * Whether a version of an app is in the registry.
     *
     * @param appId   the name of the app
     * @param version the image tag
     * @throws AppNotFoundException when no configured app has that id, or the app isn't released
     */
    public boolean hasVersion(String appId, String version) {
        return version != null && release.listTags(getReleaseImage(appId).image()).contains(version);
    }

    /**
     * Releases a commit of an app as a version and records it
     *
     * @param appId      the name of the repo
     * @param tag        the version to release
     * @param commitHash the full hash of the commit to release
     * @param username   the user that triggers the release
     * @return the tag build's id, or empty when the CI tool hasn't started the build yet
     * @throws AppNotFoundException    when no configured app has that id, or the app isn't released
     * @throws InvalidReleaseException when the app is missing, or the tag or the commit isn't in the right format
     * @throws CodeToolException       when the code tool doesn't create the tag (e.g. it already exists)
     * @throws CiToolException         when the tag was created but the build didn't start
     */
    public Optional<String> release(String appId, String tag, String commitHash, String username) {

        // check if appId is valid
        getReleaseImage(appId);


        // validate the form of tag and commit hash
        if (tag == null || !RELEASE_TAG.matcher(tag).matches()) {
            throw new InvalidReleaseException("A version looks like v1.4.0, got: " + tag);
        }
        if (commitHash == null || !FULL_COMMIT.matcher(commitHash).matches()) {
            throw new InvalidReleaseException("A commit is given by its full 40-character hash, got: " + commitHash);
        }
        String auditMessage = tag + " on commit " + commitHash.substring(0, 7);

        // create tag
        try {
            code.createTag(appId, tag, commitHash);
        } catch (CodeToolException e) {
            audit.add(username, AuditEntry.ActionType.RELEASE, appId, AuditEntry.ActionResult.FAILED, null,
                    auditMessage + ": " + e.getMessage());
            throw e;
        }

        // trigger build (with release) and log to audit
        try {
            Optional<String> buildId = ci.startBuild(appId, Map.of(GIT_REF, "refs/tags/" + tag));
            String details = buildId.isPresent() ? auditMessage : auditMessage + "; build queued, but the CI tool hadn't started it yet";
            audit.add(username, AuditEntry.ActionType.RELEASE, appId, AuditEntry.ActionResult.SUCCESS,
                    buildId.orElse(null), details);
            return buildId;
        } catch (CiToolException e) {
            String message = "Tag " + tag + " was created, but its build didn't start: " + e.getMessage();
            audit.add(username, AuditEntry.ActionType.RELEASE, appId, AuditEntry.ActionResult.FAILED, null,
                    auditMessage + ": " + message);
            throw new CiToolException(message, e);
        }
    }

    private ReleaseVersion toVersion(String appId, String tag, Map<String, String> labels) {
        String commit = labels.get(LABEL_COMMIT);
        return new ReleaseVersion(tag, labels.get(LABEL_BUILD), commit,
                commit == null
                        ? null
                        : code.getCommitUrl(appId, commit), parseStringDateToInstant(labels.get(LABEL_CREATED)));
    }

    /**
     * Parse a string date (e.g., "2026-11-06T18:16:33Z") to a Java Instant; null when missing or not a date
     */
    private static Instant parseStringDateToInstant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Parse a string version to numbers (e.g., "1.10.0" -> 1, 10, 0); null for a tag in another format
     */
    private static VersionNumber parseVersionString(String tag) {
        Matcher matcher = VERSION_NUMBER.matcher(tag);
        return matcher.matches()
                ? new VersionNumber(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)))
                : null;
    }

    private record VersionNumber(int major, int minor, int patch) {

        static final Comparator<VersionNumber> HIGHEST_FIRST = Comparator.comparingInt(VersionNumber::major)
                .thenComparingInt(VersionNumber::minor)
                .thenComparingInt(VersionNumber::patch)
                .reversed();
    }

    private ReleaseImage getReleaseImage(String appId) {
        if (appId == null) {
            throw new InvalidReleaseException("No app given");
        }
        App app = apps.getApp(appId);
        if (app.release() == null) {
            throw AppNotFoundException.noRelease(appId);
        }
        return app.release();
    }
}
