# Glossary

The words we use in the code, the API, the UI and PLAN.md, and what each one means.
Use these words. Don't use another word for the same thing.

## Apps and their parts

**app**
Something we build and run, e.g. `sample-app`. Each app has a repo, a pipeline and, if it's released and
deployed, an image and a deploy job. It's configured in one block in `application.properties`:
`dashboard.apps.<app id>.*`.

**app id**
The app's key in the config, e.g. `sample-app`. The dashboard uses only this to refer to an app:
in the API (`appId`), in URLs (`/apps/sample-app`) and as the audit log's target.
It doesn't have to match the repo, job or image name.

**repo**
The app's git repository in the code tool. Settings: `repo.tool`, `repo.workspace`, `repo.name`,
`repo.token`. In the UI: "Repo: andy-test-app/sample-app (Bitbucket)".

**workspace**
The Bitbucket account that holds the repos, e.g. `andy-test-app`.

**repo token**
A Bitbucket repository access token, one per repo, with Repositories: Write. The dashboard needs it
only to create release tags. Kept in `backend/.env`, never sent to the frontend.

**pipeline**
The app's build job in the CI tool. Settings: `pipeline.tool`, `pipeline.job`. In the UI:
"Pipeline: sample-app (Jenkins)".

**job**
A pipeline's or deploy job's name in Jenkins, e.g. `sample-app`, `sample-app-deploy`. Its steps are in the
app's repo: `Jenkinsfile` for the pipeline, `Jenkinsfile.deploy` for the deploy job.

**image**
The app's Docker image name in the registry, e.g. `sample-app`. Setting: `release.image`. In the UI:
"Image: sample-app (Docker Registry)".

**release** (config)
The `release.*` block of an app. Optional. An app without it can't be released or deployed.

**deploy** (config)
The `deploy.*` block of an app: its deploy job (`deploy.tool`, `deploy.job`). Optional. An app without it
can't be deployed and doesn't show in the environments.

**Helm chart**
How an app is installed in Kubernetes. Kept in the app's repo under `deploy/`: defaults in `values.yaml`,
and one values file per environment for what differs (replicas, memory).

## Tools

**tool**
The product that does one stage, e.g. `bitbucket`, `jenkins`, `docker-registry`. Each part of an app names
its tool (`repo.tool`, `pipeline.tool`, `release.tool`, `deploy.tool`).

**code tool / CI tool / release tool / operate tool**
The tool for the code, build and test, release, and operate stages. Now: Bitbucket, Jenkins, the local
Docker registry, Kubernetes. Jenkins also runs the deploys.

**registry**
A server that stores Docker images. Ours runs locally on port 5001. AWS ECR later.
Not the same as EC2 (EC2 is virtual machines).

**adapter**
The class that talks to one tool's API, e.g. `JenkinsAdapter`, `BitbucketAdapter`,
`DockerRegistryAdapter`, `KubernetesAdapter`. Named per tool. The Kubernetes adapter only reads.

**profile** (planned)
A config that picks the tool for each stage, e.g. `code: bitbucket`, `ci: jenkins`. Waits until we
add a second tool.

## Build and test

**build**
One run of a job. Its id is the Jenkins build number, e.g. `12` (`buildId` in the API).

**build status**
`RUNNING` (from the moment the build leaves Jenkins' queue until it ends), `SUCCESS`, `FAILED`, `UNSTABLE`,
`ABORTED`, or `UNKNOWN`.

**run build**
Starting a build of an app's pipeline from the dashboard. Recorded as `BUILD`.

**started by**
Who started a build. The dashboard user when started from the dashboard (from the audit log), else the
Jenkins user.

**test results**
The passed, failed and skipped counts of a build, and its failing tests. Read from Jenkins'
test report.

## Release

**release** (action)
Making a new version: the dashboard creates a git tag on a commit, then starts a build of
that tag. The build tests the code, then builds and pushes the image. Recorded as `RELEASE`.

**tag**
The git tag for a release: `vX.Y.Z`, e.g. `v1.4.0`.

**version**
The image's tag: the git tag without the `v`, e.g. `1.4.0`. So the image is `sample-app:1.4.0`.
Compared by number: `1.0.10` is higher than `1.0.9`.

**tag build**
A build of a git tag. Only tag builds push an image. Builds of a branch run Build and Test only.

**GIT_REF**
The Jenkins job parameter that says what to build: a branch (default `*/main`) or a tag
(`refs/tags/v1.4.0`).

**image labels**
The commit, build number and release time stored on each image. They link a version to its build
and commit.

## Deploy and operate

**environment**
Where an app runs: `dev`, `staging`, `prod`, in that order (`dashboard.environments`). One Kubernetes
namespace each.

**next environment**
The one after an environment in `dashboard.environments`, e.g. staging after dev. Where a promote goes.
The last environment has none.

**confirm environments**
The environments where a deploy, promote or rollback asks you to type the environment's name first:
`dashboard.confirm-environments`, now `prod`.

**deploy job**
The app's Jenkins job that installs a version in an environment with Helm, e.g. `sample-app-deploy`.
Parameters: `VERSION` and `ENVIRONMENT`. Every deploy, promote and rollback is one build of it.

**deploy** (action)
Installing a released version of an app in an environment, through the deploy job. Recorded as `DEPLOY`.

**deploy history**
The deploy job's builds, newest first: which version went to which environment, and whether it worked.
The dashboard reads the newest `dashboard.jenkins.max-builds` of them.

**running version**
The version an environment runs now, read from Kubernetes.

**health**
Whether all of an app's pods in an environment are ready: healthy, or unhealthy with ready / desired pods,
e.g. "Unhealthy 0/1".

**promote**
Deploying the version running in an environment to the next environment, e.g. dev → staging. Recorded as
`PROMOTE`.

**rollback**
Deploying an older version to an environment: one that deployed successfully there before and is lower than
the running one. Recorded as `ROLLBACK`.

**previous good version**
The highest version that deployed successfully to an environment and is lower than the one running there.
The version a rollback picks by default.

**rollback options**
All the versions a rollback in an environment can go to, highest first. The first is the previous good
version.

## Audit log

**audit log**
The record of every action taken through the dashboard: who, what, when, and whether it worked.
Shown on the History page.

**action type**
What was done: `BUILD`, `RELEASE`, `DEPLOY`, `PROMOTE`, `ROLLBACK`.

**action result**
`SUCCESS` or `FAILED`. A failed action is recorded with the reason. A bad request (e.g. nothing to roll back
to) is refused and not recorded.

**target**
What the action was done to. Always the app id, e.g. `sample-app`.

## Dashboard

**polling**
Loading a view again every `dashboard.frontend.poll-interval` (3s) while something in it runs: a build, or a
deploy. Stops when nothing runs. The frontend reads the interval from `/api/status`.

**refresh**
The Refresh button on a table or page: loads it again once, by hand.

**unavailable**
A part of a view whose tool couldn't be reached. It shows empty and marked "Unavailable"; the rest of the
view still shows.

## Naming rules

- Refer to an app by its app id everywhere outside the config. Never by repo, job or image name.
- The API names the app `appId`, in requests and responses (the deploy response still says `app`).
- URLs: `/apps/<app id>/<tab>`, `/environments/<environment>`.
- Config: `dashboard.<tool>.*` for a tool's settings (URL, limits),
  `dashboard.apps.<app id>.*` for an app, `dashboard.frontend.*` for what the frontend reads.
- Model classes start with their stage: `Code*`, `Ci*`, `Release*`, `Deploy*`, `Operate*`, `Environment*`.
- Services and controllers are named per tool: `BitbucketCodeService`, `JenkinsCiController`,
  `DockerRegistryReleaseService`, `JenkinsDeployService`, `KubernetesEnvironmentService`.
