package com.phytion.dashboard.model;

import java.util.List;

/**
 * An app's recent deploys to every environment, each with the commit its version was built from.
 *
 * @param appId        the app deployed
 * @param environments the configured environments, in the config's order
 * @param deploys      the deploys, newest first
 * @param unavailable  the parts whose tool couldn't be reached
 */
public record DeployHistory(String appId, List<String> environments, List<Entry> deploys, List<Part> unavailable) {

    public enum Part {
        COMMITS // from the release tool
    }

    /**
     * One deploy and the commit of the version it deployed.
     *
     * @param run       the deploy build, with its version, environment, who started it and its action
     * @param commit    the full hash of the commit the version was built from, or null when the release tool doesn't
     *                  know it (the image is gone, has no labels, or the release tool is down)
     * @param commitUrl that commit's page in the code tool, or null when the commit is
     */
    public record Entry(DeployRun run, String commit, String commitUrl) {
    }
}
