# the image that jenkins runs in
FROM jenkins/jenkins:lts-jdk25

# jenkins pushes images to docker registry
COPY --from=docker:28.3.3-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker:28.3.3-cli /usr/local/libexec/docker/cli-plugins/docker-buildx /usr/local/libexec/docker/cli-plugins/docker-buildx

# jenkins tells helm so helm can talk to kubernetes what to run
COPY --from=alpine/helm:4.3.1 /usr/bin/helm /usr/local/bin/helm
