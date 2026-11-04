package com.phytion.dashboard.service;

/**
 * The API was asked about a pipeline that isn't in the configured list.
 */
public class PipelineNotFoundException extends RuntimeException {

    public PipelineNotFoundException(String pipelineId) {
        super("Unknown pipeline: " + pipelineId);
    }
}
