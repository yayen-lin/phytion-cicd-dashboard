# Glossary

The words we use in the code, the API, the UI and PLAN.md, and what each one means.
Use these words. Don't use another word for the same thing.

## Apps and their parts

**app**
Something we build and run, e.g. `sample-app`. Each app has a repo, a pipeline and,
if it's released, an image. It's configured in one block in `application.properties`:
`dashboard.apps.<app id>.*`.

**app id**
The app's key in the config, e.g. `sample-app`. The dashboard uses only this to refer to an app:
in the API (`appId`), in URLs (`?app=sample-app`) and as the audit log's target.
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
The app's job in the CI tool. Settings: `pipeline.tool`, `pipeline.job`. In the UI:
"Pipeline: sample-app (Jenkins)".

**job**
The pipeline's name in Jenkins, e.g. `sample-app`. Its steps are in the `Jenkinsfile` in the app's repo.

**image**
The app's Docker image name in the registry, e.g. `sample-app`. Setting: `release.image`. In the UI:
"Image: sample-app (Registry)".

**release** (config)
The `release.*` block of an app. Optional. An app without it isn't shown on the Release page.

## Tools

**tool**
The product that does one stage, e.g. `bitbucket`, `jenkins`, `registry`. Each part of an app names
its tool (`repo.tool`, `pipeline.tool`, `release.tool`).

**code tool / CI tool / release tool**
The tool for the code, build and test, and release stages. Now: Bitbucket, Jenkins, the local
Docker registry.

**registry**
A server that stores Docker images. Ours runs locally on port 5001. AWS ECR later.
Not the same as EC2 (EC2 is virtual machines).

**adapter**
The class that talks to one tool's API, e.g. `JenkinsAdapter`, `BitbucketAdapter`,
`RegistryAdapter`. Named per tool.

**profile** (planned)
A config that picks the tool for each stage, e.g. `code: bitbucket`, `ci: jenkins`. Waits until we
add a second tool.

## Build and test

**build**
One run of a pipeline. Its id is the Jenkins build number, e.g. `12` (`buildId` in the API).

**run build**
Starting a build from the dashboard. Recorded as `TRIGGER_BUILD`.

**started by**
Who started a build. Comes from Jenkins.

**test results**
The passed, failed and skipped counts of a build, and its failing tests. Read from Jenkins'
test report.

## Release

**release** (action)
Making a new version: the dashboard creates a git tag on a commit on main, then starts a build of
that tag. The build tests the code, then builds and pushes the image. Recorded as `RELEASE`.

**tag**
The git tag for a release: `vX.Y.Z`, e.g. `v1.4.0`.

**version**
The image's tag: the git tag without the `v`, e.g. `1.4.0`. So the image is `sample-app:1.4.0`.

**tag build**
A build of a git tag. Only tag builds push an image. Builds of main run Build and Test only.

**GIT_REF**
The Jenkins job parameter that says what to build: a branch (default `*/main`) or a tag
(`refs/tags/v1.4.0`).

**image labels**
The commit, build number and release time stored on each image. They link a version to its build
and commit.

## Audit log

**audit log**
The record of every action taken through the dashboard: who, what, when, and whether it worked.

**action type**
What was done: `TRIGGER_BUILD`, `RELEASE`. Later: `DEPLOY`, `PROMOTE`, `ROLLBACK`.

**action result**
`SUCCESS` or `FAILED`. A failed action is recorded with the reason.

**target**
What the action was done to. Always the app id, e.g. `sample-app`.

## Deploy (planned)

**environment**
Where an app runs: `dev`, `staging`, `prod`. One Kubernetes namespace each.

**deploy**
Running a version of an app in an environment, through a Jenkins deploy job.

**promote**
Moving the exact version running in dev to staging, or staging to prod.

**rollback**
Redeploying the version that ran before in an environment.

## Naming rules

- Refer to an app by its app id everywhere outside the config. Never by repo, job or image name.
- API requests take `appId`. Responses name the app in a field called `app`.
- URL query: `?app=<app id>`.
- Config: `dashboard.<tool>.*` for a tool's settings (URL, limits),
  `dashboard.apps.<app id>.*` for an app.
- Model classes start with their stage: `Code*`, `Ci*`, `Release*`.
- Services and controllers are named per tool: `BitbucketCodeService`, `JenkinsCiController`,
  `RegistryReleaseService`.
