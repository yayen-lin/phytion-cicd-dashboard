package com.phytion.dashboard.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Helpers for services that call several tools at the same time.
 */
final class ParallelCalls {

    private ParallelCalls() {
    }

    /**
     * Waits for a call and returns what it read.
     */
    static <T> T await(CompletableFuture<T> call) {
        try {
            return call.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }
}
