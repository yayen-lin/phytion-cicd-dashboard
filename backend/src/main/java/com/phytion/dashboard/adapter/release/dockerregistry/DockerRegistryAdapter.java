package com.phytion.dashboard.adapter.release.dockerregistry;

import java.util.List;
import java.util.Map;

/**
 * Docker registry implementation (the Docker Registry HTTP API v2). Read-only.
 */
public interface DockerRegistryAdapter {

    /**
     * The tags of an image, in the registry's order
     *
     * @param image the image's name in the registry
     * @return the tags, or an empty list when nothing was pushed under that name yet
     */
    List<String> listTags(String image);

    /**
     * The labels an image version was built with
     *
     * @param image the image's name in the registry
     * @param tag   version tag
     * @return the labels, or an empty map when the registry has no such tag or the image has no labels
     */
    Map<String, String> getLabels(String image, String tag);
}
