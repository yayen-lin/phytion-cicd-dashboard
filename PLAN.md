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

What's Done:
- [x] 1.1 Docker Desktop installed (old computer). Node.js not installed yet
- [x] 1.2a Backend skeleton in `backend/`: Spring Boot 4.1.2, Java 21, Gradle (Groovy),
      package `com.phytion.dashboard`, only the web dependency.
      `GET /api/status` returns `{"status":"ok"}`. `./gradlew build` passes.
- [x] 1.2b Frontend skeleton in `frontend/`: Vite React + TypeScript template, proxy
      `/api` to the backend on port 8080, show the `/api/status` result on the page
- [x] 1.3 Jenkins in Docker, on port **8081** (backend uses 8080). Do this next
- [x] 1.4 Sample app repo in the Bitbucket workspace `andy-test-app`
      (https://bitbucket.org/andy-test-app/workspace/overview/): tiny Spring Boot app
      with a `Jenkinsfile`
- [x] 1.5 Login with a single user from a file (Spring Security)

What's Next:
- [x] 1.6 CI adapter interface + read-only Jenkins adapter: `CiAdapter` with `listRuns(job)`,
      `getRun(job, id)`; `JenkinsAdapter` calls the Jenkins REST API with URL/user/token from
      environment variables; `GET /api/ci/runs`; frontend lists the sample-app's builds
      (number, status, commit, time)
- [x] 1.7 "Run build" button + action history: `triggerRun(job)` on the adapter; Spring Data JPA
      + file-based H2; `Action` entity (who, what, target, when, result); `POST /api/ci/runs`
      triggers the build and records the action; history table in the UI
- [ ] 1.8a Frontend look: shadcn/ui (Tailwind) as the component library; app shell (sidebar with
      the CI/CD stages, only Build works so far; top bar with theme and user menus; routing);
      restyle the login page, builds table (status badges) and history table.
      Build page: pipeline selector (`jenkins:sample-app`, listed in `dashboard.ci.pipelines` in
      the backend config, served by `GET /api/ci/pipelines`), tabs Builds (`/build/builds`) and
      History (`/build/history`, the audit log filtered to that pipeline)
- [ ] 1.8 Build logs and test results: `getLog(job, id)` on the adapter, log viewer page,
      test results from Jenkins' JUnit report API
- [ ] 1.9 Polling: frontend refetches runs every few seconds while a build is running
- [ ] 1.10 Bitbucket adapter + profiles: `CodeAdapter` (branches, recent commits),
      `BitbucketAdapter`, profile config (`code: bitbucket`, `ci: jenkins`) selects the adapters

