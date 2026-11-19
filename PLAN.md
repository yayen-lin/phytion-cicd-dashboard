## Phytion CI/CD Dashboard

It's a single page that connects to all the CI/CD tools. 

### Phytion EcoTracer
https://phytionassetmanagement.com

- Converts cow manure into hydrogen
- The hydrogen powers cars, trucks, generators, tractors, etc.
- The emissions this avoids (mainly methane from manure) are sold as carbon credits to manufacturers worldwide
- Tech side: sensors on vehicles and facilities collect data, and a data pipeline calculates how many credits were generated
- (not sure) Credits get audited, so we need to know which version of the calculation code produced which credits

### Current situation (as of 2026-11-19)
- Phytion's team is two people: one writes the services (the data pipeline), the other installs the
  hardware that generates the data (flow meters, GPS on trucks and cars, etc.)
- The services are mostly built and wait for the hardware installation to finish
- No QA or UAT people yet; may be needed later
- No Jira or other planning tool yet
- I'm building the dashboard so it's ready when they are

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

### Decisions
Choices that hold for the whole dashboard, with why. Words are defined in GLOSSARY.md.

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
- Rollback deploys an older version through the deploy job, not `helm rollback` (which bounces between the
  last two versions). Its options come from the deploy job's history in Jenkins
- Polling only while something runs, one request per view; webhooks wait until the dashboard runs on AWS
- Three environments: `dev`, `staging`, `prod`. `qa` and `uat` are added when Phytion has QA or UAT people;
  until then staging runs the integration tests and holds the manual approval

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
- AWS services, who can deploy to prod (deploy stage)
- Runtime on AWS: EKS (Kubernetes run by AWS; keeps the Helm deploys and Kubernetes adapter) or plain EC2
  (deploys, restarts and health checks built by us, one instance per environment)
- How they version releases (release stage)
- Monitoring tool (monitor stage)
- Planning tool, e.g. Jira (plan stage)

### Next steps
_to discuss_

---

### Worklog

Step 1: Skeleton
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
- [x] 1.9 Refresh buttons on the tables (polling came later, in 2.12)
- [x] 1.10 Bitbucket adapter (read-only, no token: repos are public) + Code page: repo selector,
      Branches and Commits tabs. Repos and CI pipelines listed in `application.properties`.
      Adapters are named per tool (`JenkinsAdapter`, `BitbucketAdapter`); shared interfaces and
      profiles wait until a second tool shows the shape

Step 2: test results, release, deploy and operate.

- [x] 2.0 Test page: per-pipeline passed / failed / skipped counts and a build's failing tests, from
      Jenkins' test reports
- [x] 2.1 Release setup: Docker CLI + socket for Jenkins, a local registry on port 5001, a `Dockerfile`
      in each app
- [x] 2.2 Tag builds: Jenkins jobs take `GIT_REF`; the `Release` stage builds, labels (commit, build,
      release time) and pushes the image only for a `vX.Y.Z` tag
- [x] 2.3 Release: versions in the registry, highest first; "Release" tags a commit through Bitbucket's
      API, starts the tag build and records `RELEASE`
- [x] 2.4 Environments: Kubernetes in Docker Desktop, Helm, namespaces `dev`, `staging`, `prod`; the apps
      got Actuator health probes
- [x] 2.5 Helm chart per app (`deploy/`) with one values file per environment
- [x] 2.6 Deploy: a Jenkins deploy job per app (`Jenkinsfile.deploy`, `VERSION` + `ENVIRONMENT`) runs
      `helm upgrade --install --wait`; the dashboard triggers it and records `DEPLOY`
- [x] 2.7 Kubernetes adapter (read-only): each environment's running version, commit and health, and
      who deployed it and when
- [x] 2.8 Environment-first layout: Overview, Apps, Environment, App (tabs) and History pages
- [x] 2.9 Promote: the version running in one environment goes to the next; type-to-confirm for
      `dashboard.confirm-environments`; records `PROMOTE`
- [x] 2.10 Rollback: pick an older version that deployed successfully there (the previous good one
      picked by default); type-to-confirm like Promote; records `ROLLBACK`
- [x] 2.11 One action menu everywhere: Run build, Release, Deploy, Promote, Rollback (Apps page,
      Environment page, an app's Environments tab)
- [x] 2.12 Polling: a view with a running build or deploy loads again every
      `dashboard.frontend.poll-interval` (3s, read from `/api/status`) until it ends; deploy dialogs
      reload the table behind them when their build finishes; Refresh buttons stay
- [x] 2.13 Links that open another site or tab end with an ↗ icon

Step 3: Industry Standard
- [x] 3.1 user management, db set up and stuff so we can track what will be helpful on the dashboard? 
  - [x] 3.1.1 Postgres container in `jenkins/docker-compose.yml` (port 5432, with a volume); the backend switches
        from H2 to Postgres, connection settings in `.env`
  - [x] 3.1.2 Squash the H2 migrations into one Postgres `V1`; drop the old H2 data (`backend/data/`)
  - [x] 3.1.3 `users` table: username, password hash (BCrypt), role, active, created time
  - [x] 3.1.4 Login reads users from the database instead of `users.properties` (then removed); a deactivated
        user can't log in
  - [x] 3.1.5 Roles `VIEWER` (look only), `DEVELOPER` (build, release, deploy / promote / rollback outside prod),
        `ADMIN` (everything, prod included, and users); the backend refuses actions the role doesn't allow
  - [x] 3.1.6 Users API (admin only): list, add (username, password, role), change role, deactivate and
        reactivate; each recorded in the audit log
  - [x] 3.1.7 Users page (admin only); action buttons the user's role can't use are hidden (my question when get
    to this: hidden and those check in the backend would still catch it right? if says a user uncheck the hidden 
    attribute in the vss on the dashboard website)
  - Later: change your own password, admin resets a password
- [ ] 3.2 Safe actions: what happens when an action fails partway
  - [ ] 3.2.1 Audit record written as `PENDING` before calling the tool, then set to `SUCCEEDED` or `FAILED`;
        records stuck in `PENDING` (e.g. the backend restarted) are checked against the tool on startup
  - [ ] 3.2.2 Deploy lock: one deploy, promote or rollback per app and environment at a time; the backend
        refuses a second one while the first runs
  - [ ] 3.2.3 Safe retries: clicking twice or retrying a request doesn't start the action twice
- [ ] 3.3 Gates and approvals: each environment has a job and checks a version must pass before it moves on
  - [ ] 3.3.1 Gates per environment in config: dev (unit tests and scans pass), staging (integration tests
        pass, one manual approval), prod (an admin approves, not the person who released it)
  - [ ] 3.3.2 Integration tests: a Jenkins job runs them against staging after each deploy there
  - [ ] 3.3.3 Approvals: approve or reject a version in an environment, with a comment; recorded in the audit log
  - [ ] 3.3.4 Each version shows its gates (passed, failed, waiting); Promote stays disabled until all pass
- [ ] 3.4 Quality checks in the build: results attach to the version and show on the Release page
  - [ ] 3.4.1 Code coverage (JaCoCo)
  - [ ] 3.4.2 Static analysis (SonarQube)
  - [ ] 3.4.3 Security scans of the image and its libraries (Trivy or OWASP Dependency-Check)
  - [ ] 3.4.4 SBOM and image signing (cosign)
- [ ] 3.5 Traceability: Jira ticket → commit → build → image → deploy → pipeline run → credits
  - Waits for Phytion's data pipeline to run and a planning tool to be picked
  - [ ] 3.5.1 Pipeline runs: which version ran, when, on which input, and which credits it produced
  - [ ] 3.5.2 Planning tool: tickets linked to commits by their key in the commit message
  - [ ] 3.5.3 Versions and audit records can't be edited or deleted
- [ ] 3.6 DORA metrics from Jenkins, Kubernetes and the audit log: deploy frequency, time from commit to prod,
      share of deploys that fail, time to recover
- [ ] 3.7 Operate and monitor
  - [ ] 3.7.1 Pods: status, restarts, and the version each replica runs
  - [ ] 3.7.2 Batch apps (Job / CronJob): last run, next run, result; daemon apps: health and uptime
  - [ ] 3.7.3 Datadog: alerts and error rate after each deploy, linked from the deploy (waits for Datadog)
  - [ ] 3.7.4 AWS: instance status and cost this month (waits for AWS)
- Later: canary and blue-green deploys, feature flags, GitOps (Argo CD), `qa` and `uat` environments when
  Phytion has people to use them
- [ ] add-ons: artifactories? managing them? a real app hosted on ec2 to simulate it?  


