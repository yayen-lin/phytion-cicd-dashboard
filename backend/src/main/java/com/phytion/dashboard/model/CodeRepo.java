package com.phytion.dashboard.model;

/**
 * A code repo the dashboard shows, and the tool that hosts it. Configured in application.properties.
 *
 * @param tool      the external tool that hosts the repo
 * @param workspace the workspace the repo is in
 * @param name      the name of the repo in that workspace
 * @param token     an access token to this repo with write permission for creating a tag
 */
public record CodeRepo(String tool, String workspace, String name, String token) {

    /**
     * In the form the API refers to the repo
     */
    public String id() {
        return tool + ":" + workspace + "/" + name;
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    @Override
    public String toString() {
        return "CodeRepo[tool=" + tool
                + ", workspace=" + workspace
                + ", name=" + name
                + ", token=" + (hasToken() ? "(set)" : "(none)")
                + "]";
    }
}
