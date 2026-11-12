package com.phytion.dashboard.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Where an app's released images go.
 *
 * @param tool  which release tool stores the images
 * @param image the name of the image in that tool
 */
public record ReleaseImage(@NotBlank String tool, @NotBlank String image) {
}
