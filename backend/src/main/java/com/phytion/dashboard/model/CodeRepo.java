package com.phytion.dashboard.model;

import jakarta.validation.constraints.NotBlank;

/**
 * An app's code repo, and the tool that hosts it.
 *
 * @param tool      the external tool that hosts the repo
 * @param workspace the workspace the repo is in
 * @param name      the name of the repo in that workspace
 * @param token     an access token to this repo with write permission for creating a tag
 */
public record CodeRepo(@NotBlank String tool, @NotBlank String workspace, @NotBlank String name, String token) {

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
