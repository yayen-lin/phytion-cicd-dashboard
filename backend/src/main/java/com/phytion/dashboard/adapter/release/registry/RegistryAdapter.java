package com.phytion.dashboard.adapter.release.registry;

import java.util.List;
import java.util.Map;

/**
 * Docker registry implementation (the Docker Registry HTTP API v2). Read-only.
 */
public interface RegistryAdapter {

    /**
     * The tags of an image, in the registry's order (not by version).
     *
     * @param image the image's name in the registry, e.g. "sample-app"
     * @return the tags, or an empty list when nothing was pushed under that name yet
     */
    List<String> listTags(String image);

    /**
     * The labels an image version was built with (docker build --label), e.g.
     * "org.opencontainers.image.revision" -> the commit.
     *
     * @param image the image's name in the registry, e.g. "sample-app"
     * @param tag   e.g. "1.4.0"
     * @return the labels, or an empty map when the registry has no such tag or the image has no labels
     */
    Map<String, String> getLabels(String image, String tag);
}
