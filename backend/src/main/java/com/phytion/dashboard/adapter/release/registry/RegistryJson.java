package com.phytion.dashboard.adapter.release.registry;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The registry's JSON responses that RegistryAdapter reads. Everything else is ignored.
 */
final class RegistryJson {

    private RegistryJson() {
    }

    /** GET /v2/{image}/tags/list. "tags" is null when every tag was deleted */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Tags(String name, List<String> tags) {
    }

    /**
     * GET /v2/{image}/manifests/{tag}: either one image ("config" is set) or an index of images, one per platform
     * ("manifests" is set). Docker Desktop pushes an index: the image plus an attestation (build provenance) entry.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Manifest(String mediaType, Descriptor config, List<IndexEntry> manifests) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Descriptor(String digest) {
    }

    /** An image in an index. Attestation entries have platform "unknown/unknown" */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record IndexEntry(String digest, Platform platform) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Platform(String os, String architecture) {
    }

    /**
     * GET /v2/{image}/blobs/{digest} of the image's config. Its own "created" date isn't read: when the files didn't
     * change since an earlier build, Docker reuses that build, and its old date with it
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Config(ImageSettings config) {
    }

    /** The image's settings: "Labels" is null when the image has none */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ImageSettings(@JsonProperty("Labels") Map<String, String> labels) {
    }
}
