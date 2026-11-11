package com.phytion.dashboard.model;

import java.time.Instant;

/**
 * One commit
 *
 * @param hash        the full commit hash
 * @param message     the full commit message
 * @param author      the Bitbucket user's name, or the git author's name when they have no Bitbucket account
 * @param committedAt when it was committed
 * @param url         the commit's page in Bitbucket
 */
public record CodeCommit(
        String hash,
        String message,
        String author,
        Instant committedAt,
        String url) {
}
