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
- A release is a git tag `vX.Y.Z` (e.g. `v1.4.0`) on a commit on any branch, made with the
  dashboard's "Release" button through Bitbucket's API. Only builds of a tag push an image;
  builds of a branch run Build and Test only
- Image tag = the git tag without the `v`, e.g. `sample-app:1.4.0`. The image's labels hold the
  commit, the Jenkins build number and the release time, so every version points to one build and
  one commit (the base for tracing credits back to code). The image's own "created" date isn't
  used: Docker keeps an earlier build's date when the app's files didn't change
- Any commit on any branch can be released (not only main, to stay flexible): the tag build runs
  Build and Test again, so a failing commit gets no image. A version whose tag build failed is
  skipped; release the next one (e.g. `v1.4.1`)
- No webhooks yet, so Jenkins doesn't see new tags by itself: the dashboard creates the tag, then
  starts a build of that tag (the Jenkins job takes a parameter: the branch or tag to build)
- Jenkins deploys (a deploy job with app, version and environment as parameters); the dashboard
  triggers it and records it in the audit log. Kubernetes is only read, to see what runs where
- Jenkins gets Docker by mounting Docker Desktop's socket and adding the Docker CLI to its image
- One request per view or table: the backend combines what a view shows (from every tool it
  needs) into one endpoint, and the frontend makes one call for it. A tool that can't be reached
  leaves its part empty and marked unavailable instead of failing the whole response
- The tools are the record of what happened: builds, releases and deploys are read from the
  tools (Jenkins, the registry, Kubernetes), so ones started outside the dashboard (e.g. straight
  in Jenkins) show too. The audit log only adds which dashboard user did it (the tools only see
  the dashboard's own user), and keeps attempts that never reached a tool

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
- [x] 2.3 Deploy: a Jenkins deploy job per app (`Jenkinsfile.deploy`, parameters `VERSION` and
      `ENVIRONMENT`) runs `helm upgrade --install --wait` through the `jenkins-deployer` account; the
      dashboard triggers it and records `DEPLOY`.
- [x] 2.4 Environment overview: a read-only Kubernetes adapter shows each environment's running
      version, commit and health, and who deployed it and when
- [x] 2.5 Promotion: "Promote" moves the exact version running in dev to staging, or staging to
      prod. Prod asks "are you sure?" (type the environment name to confirm). Recorded as `PROMOTE`
  - [x] 1. Backend: `PROMOTE` in `AuditEntry.ActionType`; `listDeploys` also reads `PROMOTE` entries
           for who started a deploy build (today only `DEPLOY`, so a promote shows Jenkins' user)
  - [x] 2. Backend: a promote method next to `deploy` (app, source environment): the next environment
           is the next one in `dashboard.environments`; the version is the one Kubernetes says runs in
           the source environment (no health check: whoever promotes decides). Fails when nothing runs
           there, there's no next environment, the next environment already runs the same or a higher
           version (going back is Rollback's job, or a plain Deploy), or the version isn't in the
           registry. Triggers the deploy job with that version and records `PROMOTE`, also when it
           fails, with the reason (a bad request, e.g. nothing to promote, gets a 400 and isn't
           recorded, like Deploy); a POST endpoint; tests
  - [x] 3. Frontend: Promote dialog ("sample-app 1.0.1: staging → prod"); environments in
           `dashboard.confirm-environments` ask to type the target environment's name; then shows the
           started deploy build with a link to Jenkins, like the Deploy dialog
  - [x] 4. Environment page: turn on the Promote action; hidden in the last environment
  - [x] 5. Check end to end: promote dev → staging → prod, see the pods in
           `kubectl get pods -n <env>` and the `PROMOTE` entries on the History page
- [ ] 2.6 Rollback: go back to the version that ran before in that environment, with
      `helm rollback` (Helm keeps each install's history) run by a Jenkins job. Confirmation for
      prod; recorded as `ROLLBACK`
  - [ ] 1. Rollback pipeline: `Jenkinsfile.rollback` in each app (parameter `ENVIRONMENT`), a job
           separate from the deploy job. It rolls back to the last revision that ran (`superseded` in
           `helm history`, not a failed one) with `helm rollback <app> <revision> -n <env> --wait`, so
           the job fails when the pods don't get ready. Jenkins jobs `sample-app-rollback` and
           `dummy-app-rollback`. Check by running one from Jenkins' UI
  - [ ] 2. Backend: each app's rollback job in `application.properties` (`deploy.rollback-job`);
           `ROLLBACK` in `AuditEntry.ActionType`
  - [ ] 3. Backend: a GET endpoint for the version a rollback goes back to (from the deploy job's
           history: the last successful deploy to that environment before the current one), so the
           dialog shows "1.0.2 → 1.0.1" before confirming
  - [ ] 4. Backend: a rollback method (app, environment): triggers the rollback job and records
           `ROLLBACK` with the from and to versions, also when it fails, with the reason; a POST
           endpoint; tests
  - [ ] 5. Backend: read the rollback job's builds next to the deploy job's, so the Environment page
           and Overview show who rolled back and when (also rollbacks started in Jenkins)
  - [ ] 6. Frontend: Rollback dialog with the from and to versions; the same type-to-confirm for
           `dashboard.confirm-environments`. Environment page: turn on the Rollback action, disabled
           when there's nothing to roll back to
  - [ ] 7. Check end to end: deploy 1.0.1 then 1.0.2 to staging, roll back, see 1.0.1 running and
           the `ROLLBACK` entry on the History page
- [] 2.7 user management
