## Phytion CI/CD Dashboard

It's a single page that connects to all the CI/CD tools. 

### Phytion EcoTracer
https://phytionassetmanagement.com

- Converts cow manure into hydrogen
- The hydrogen powers cars, trucks, generators, tractors, etc.
- The emissions this avoids (mainly methane from manure) are sold as carbon credits to manufacturers worldwide
- Tech side: sensors on vehicles and facilities collect data, and a data pipeline calculates how many credits were generated
- (not sure) Credits get audited, so we need to know which version of the calculation code produced which credits

---

### Goal
One page where the Phytion team can see and control their whole CI/CD pipeline
(code, build, test, release, deploy, operate, monitor, plan) without switching between tools.

- Keeps a history of every action taken through the dashboard (who did what, and when):
  triggering builds, deploys, rollbacks, and so on
- Tools connect through adapters, so switching tools (e.g. Bitbucket → GitHub) doesn't
  require rewriting the dashboard
- Makes it easy to trace which version of the calculation code produced which carbon credits

### Users
- For now: just me
- Users stored in a file for now (no user management yet)
- Later: proper users and roles (e.g. who can deploy to prod)


### New Note
- Apps will run on AWS EC2
- There will be many apps
- Will have multiple environments
- Should be industry standard
- Batch applications (run when needed) vs daemon applications (run at all time) on dashboard
- some info about the instance such as cost this month and its status and whatnot

### Thoughts
- Makes the release and build/test button on code menu

### Features

Now:
- Status of each pipeline stage
- Buttons that trigger actions (run build, deploy, rollback, ...)
- Action history: who did what, and when
- Environments (dev, staging, prod), each with its own deploy config
- Credentials: API tokens for Bitbucket, Jenkins, AWS kept in environment variables
  (AWS Secrets Manager later), never in the profile or in git
- Build logs and test results, viewable in the dashboard
- Versions and environment overview: built versions available to deploy, and which
  version is running in each environment
- Promotion dev → staging → prod, with an "are you sure?" confirmation for prod
- Rollback to the previous version
- Live status by polling every few seconds

Later:
- Notifications (Slack / email) on failed builds and finished deploys
- Webhooks from Bitbucket and Jenkins instead of polling (needs the dashboard reachable from the internet)
- Deploy lock: no two deploys to the same environment at once
- Tool connection status (e.g. Jenkins is down, a token expired)
- Data pipeline runs (the jobs that calculate credits), to confirm with the team

Out of scope:
- Defining pipelines in the dashboard. Pipeline steps live in a `Jenkinsfile` in the repo;
  the dashboard only triggers and shows them

### Pipeline

1. Code
   - Bitbucket: 
     - test app: https://bitbucket.org/andy-test-app/sample-app/src/main/
     - phytion: TBD
2. Build
   - Jenkins runs it (tool depends on the app's language)
3. Test
   - Jenkins runs it (tool depends on the app's language)
4. Release
   - Jenkins
5. Deploy
   - docker
6. Operate
    - kubernetes
7. Monitor
   - datadog
8. Plan
   - no yet

Notes:
- Jenkins is a server we have to run ourselves: local Docker for testing, AWS later

### Plugin design (adapters / profiles)
- Keep it light: one interface per tool type (e.g. "a CI tool can list runs, trigger a run, show logs")
- Only build the Bitbucket and Jenkins adapters for now
- A profile is a simple config, e.g. `code: bitbucket`, `ci: jenkins`
- Adding GitHub / GitHub Actions later = writing new adapters, not rewriting the dashboard
- Expect to adjust the interfaces when a second tool is added

### Dashboard Stacks
- backend tech stacks
  - Java + Spring Boot
  - Spring Security for login
  - Spring Data JPA + a database for the action history (every action taken through the
    dashboard: who did it, what it was, and when)
- frontend tech stacks
  - React + TypeScript, set up with Vite
  - Separate app that talks to the Spring Boot API
  - Component library: not picked yet (e.g. MUI or shadcn/ui)

### Open questions

Needed now:
1. Which pages/buttons do they use every day on their work dashboard?
2. Do they have a Bitbucket workspace, or do we create one?

Needed later:
- App language and frameworks (when setting up their repo's pipeline)
- AWS services, environments (dev/staging/prod), who can deploy to prod (deploy stage)
- How they version releases (release stage)
- Monitoring tool (monitor stage)
- Planning tool, e.g. Jira (plan stage)

### Next steps
_to discuss_

---

### Worklog

Step 1 (done, for reference):
- [x] 1.1 Docker Desktop installed
- [x] 1.2 Skeletons: backend (Spring Boot, `backend/`) and frontend (Vite React + TS, `frontend/`, proxies `/api` to :8080)
- [x] 1.3 Jenkins in Docker on port 8081
- [x] 1.4 Bitbucket workspace `andy-test-app`: `sample-app` and `dummy-app` (Spring Boot + `Jenkinsfile`
      with Build and Test stages), each with a Jenkins job
- [x] 1.5 Login: one user from a file (Spring Security)
- [x] 1.6 Jenkins adapter (read-only): list a job's builds
- [x] 1.7 "Run build" button + audit log (H2 + JPA): who did what, and when
- [x] 1.8a Frontend look: shadcn/ui, sidebar with the CI/CD stages, Build page with pipeline selector
      and Builds / History tabs
- [x] 1.8 Build details modal (status, commit, branch, duration, who started it, links to Jenkins)
- [x] 1.9 Refresh buttons on the tables (no polling)
- [x] 1.10 Bitbucket adapter (read-only, no token: repos are public) + Code page: repo selector,
      Branches and Commits tabs. Repos and CI pipelines listed in `application.properties`.
      Adapters are named per tool (`JenkinsAdapter`, `BitbucketAdapter`); shared interfaces and
      profiles wait until a second tool shows the shape

Step 2: test results, then release, deploy and operate.

Decisions:
- Apps run locally on Docker Desktop's built-in Kubernetes, one namespace per environment
  (`dev`, `staging`, `prod`)
- Images go to a local registry container for now (AWS ECR later)
- A release is a git tag `vX.Y.Z` (e.g. `v1.4.0`) on a commit on main, made with the dashboard's
  "Release" button through Bitbucket's API. Only builds of a tag push an image; builds of main
  run Build and Test only
- Image tag = the git tag without the `v`, e.g. `sample-app:1.4.0`. The image's labels hold the
  commit, the Jenkins build number and the release time, so every version points to one build and
  one commit (the base for tracing credits back to code). The image's own "created" date isn't
  used: Docker keeps an earlier build's date when the app's files didn't change
- Any commit on main can be released: the tag build runs Build and Test again, so a failing commit
  gets no image. A version whose tag build failed is skipped; release the next one (e.g. `v1.4.1`)
- No webhooks yet, so Jenkins doesn't see new tags by itself: the dashboard creates the tag, then
  starts a build of that tag (the Jenkins job takes a parameter: the branch or tag to build)
- Jenkins deploys (a deploy job with app, version and environment as parameters); the dashboard
  triggers it and records it in the audit log. Kubernetes is only read, to see what runs where
- Jenkins gets Docker by mounting Docker Desktop's socket and adding the Docker CLI to its image

- [x] 2.0 Test page (sidebar "Test"): the test results Jenkins already collects (the `Test` stage
      in each `Jenkinsfile` publishes JUnit results). Per pipeline: recent builds with passed /
      failed / skipped counts; a build's failing tests with their error message. Read from
      Jenkins' test report API; builds that never reached the tests show "no test results"
- [x] 2.1 Release: Docker CLI + socket for Jenkins; local registry container on port 5001; a
      `Dockerfile` in `sample-app` and `dummy-app`. Each Jenkins job takes a `GIT_REF` parameter
      (default `*/main`); the `Release` stage in each `Jenkinsfile` runs only for a tag build
      (`refs/tags/vX.Y.Z`): it builds the image, labels it with the commit, build number and
      release time, and pushes it. Release page: the versions in the registry, highest version
      first, each with its build (opens the build details) and commit (links to Bitbucket), and a
      "Release" button: pick a commit on main, type the version (format checked as you type); the
      backend creates the tag through Bitbucket's API (one repository access token per repo, with
      Repositories: Write, in `backend/.env`; reading stays anonymous), starts the tag build, and
      records `RELEASE`, also when it fails, with the reason
- [x] 2.2 Environments: Kubernetes on in Docker Desktop (kubeadm), Helm installed; namespaces
      `dev`, `staging`, `prod` (`kubectl create namespace`). `sample-app` and `dummy-app` got Spring
      Actuator with the readiness and liveness endpoints turned on. Each app keeps a Helm chart in
      its repo (`deploy/`): a Deployment (startup, liveness and readiness probes on
      `/actuator/health/...`, `APP_ENVIRONMENT`) and a Service, defaults in `values.yaml`, and one
      values file per environment for what differs (dev and staging: 1 replica, 512Mi; prod: 2
      replicas, 1Gi). The version is given at deploy time (`--set image.tag=1.0.1`). Checked by
      installing `sample-app:1.0.1` into `dev` from the local registry: its pod became ready
- [ ] 2.3 Deploy: a Jenkins deploy job (parameters: version, environment) that runs
      `helm upgrade --install` with the app's `deploy/` chart and that image version; Jenkins gets
      `helm` and access to the cluster. Dashboard: "Deploy" button (pick a version and an
      environment) that triggers the job and records `DEPLOY` in the audit log
  - [x] 1. Helm in the Jenkins image (`jenkins/Dockerfile`), rebuild the container, check
           `helm version` inside it
  - [x] 2. Cluster access for Jenkins: a service account `jenkins-deployer` allowed to deploy only
           in `dev`, `staging` and `prod`; a kubeconfig with its token that reaches the cluster from
           inside the container (`host.docker.internal:6443`, not `127.0.0.1`), stored as a Jenkins
           credential. Check with `helm list -n dev` from a test job
  - [x] 3. Deploy pipeline: `Jenkinsfile.deploy` in each app (parameters `VERSION`, `ENVIRONMENT`):
           checks out the tag `v<VERSION>` so the chart matches the version, then
           `helm upgrade --install <app> ./deploy -n <env> -f deploy/values-<env>.yaml
           --set image.tag=<VERSION> --wait`, so the job fails when the pods don't get ready.
           Jenkins jobs `sample-app-deploy` and `dummy-app-deploy`. Check by running one from
           Jenkins' UI (e.g. `sample-app` 1.0.1 to `staging`). 
  - [x] 4. Backend: each app's deploy job in `application.properties` (`deploy.tool`,
           `deploy.job`) and the environments list; a deploy service that checks the version is
           in the registry and the environment exists, triggers the job with the Jenkins adapter,
           and records `DEPLOY` (also when it fails, with the reason); a POST endpoint; tests
  - [ ] 5. Environment-first layout, before any new frontend: pages per environment and per app
           instead of per stage, reusing today's tables and dialogs. Sidebar: Overview, `dev`,
           `staging`, `prod`, Apps, History
    - Overview: one card per environment (how many apps, how many healthy, latest deploy)
    - Environment page (`/environments/<env>`): one row per app running there (version, health,
      deployed when and by whom); a row's actions dropdown: Deploy a version, Promote to the next
      environment, Rollback. Clicking a row opens the app page
    - Apps page (`/apps`): one row per app (latest build, latest version, what runs in each
      environment); a row's actions dropdown: Run build (build + test), Release, Deploy (pick an
      environment and a version; also how an app gets into an environment the first time).
      Clicking a row opens the app page
    - App page (`/apps/<app>`): tabs for Commits, Builds (with tests), Versions and Environments.
      Clicking a commit row tags it, which is the release: the same dialog as the Release action
    - Tagging a commit is releasing (`v1.0.2` makes Jenkins build image `1.0.2`); deploying is
      picking which version (tag) goes into an environment
  - [ ] 6. Frontend: one Deploy dialog (pick a version from the registry, and the environment when
           opened from the Apps page), opened from the Apps page's Deploy action and the environment
           page's "Deploy a version"; then shows the started deploy build with a link to Jenkins
  - [ ] 7. Check end to end: deploy from the dashboard, see the pods in `kubectl get pods -n <env>`
           and the `DEPLOY` entry on the History page
- [ ] 2.4 Environment overview (Deploy page): read-only Kubernetes adapter; a grid of apps x
      environments showing the running version, its commit, whether it's healthy (ready pods),
      and when and by whom it was deployed (from the audit log)
- [ ] 2.5 Promotion: "Promote" moves the exact version running in dev to staging, or staging to
      prod. Prod asks "are you sure?" (type the environment name to confirm). Recorded as `PROMOTE`
- [ ] 2.6 Rollback: go back to the version that ran before in that environment, with
      `helm rollback` (Helm keeps each install's history) run by a Jenkins job. Confirmation for
      prod; recorded as `ROLLBACK`
