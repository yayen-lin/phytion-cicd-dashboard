# Jenkins plus the Docker CLI (with buildx), so pipelines can build and push images.
# The CLI talks to Docker Desktop's engine through the socket mounted in docker-compose.yml.
FROM jenkins/jenkins:lts-jdk25

COPY --from=docker:28.3.3-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker:28.3.3-cli /usr/local/libexec/docker/cli-plugins/docker-buildx /usr/local/libexec/docker/cli-plugins/docker-buildx
