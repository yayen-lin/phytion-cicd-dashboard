import {checkLogin, post} from './client'

/** A CI job the dashboard shows, and the tool that runs it */
export type CiPipeline = {
  id: string // e.g. "jenkins:sample-app"
  tool: string // e.g. "jenkins"
  job: string // e.g. "sample-app"
}

/** Returns the configured CI pipelines, in the order the backend lists them. */
export async function getPipelines(): Promise<CiPipeline[]> {
  const res = await checkLogin(fetch('/api/ci/pipelines'))
  if (!res.ok) throw new Error(`GET /api/ci/pipelines failed: ${res.status}`)
  return res.json()
}

export type CiBuildStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'UNSTABLE' | 'ABORTED' | 'UNKNOWN'

/** One build of a CI job */
export type CiBuild = {
  id: string
  status: CiBuildStatus
  commit: string | null
  branch: string | null
  startedAt: string
  durationMillis: number | null // null while running
  url: string
  tests: CiTestCounts | null // null when the build has no test results (running, or stopped before the tests)
}

export type CiTestCounts = {
  passed: number
  failed: number
  skipped: number
}

/** One test of a build */
export type CiTestCase = {
  className: string // e.g. "com.phytion.dummyapp.CreditCalculatorTests"
  name: string // e.g. "noMethaneIsNoCredits()"
  status: 'PASSED' | 'FAILED' | 'SKIPPED'
  durationMillis: number
  errorMessage: string | null // set only for failed tests
}

/** All test results of one build */
export type CiTestReport = {
  counts: CiTestCounts
  durationMillis: number
  cases: CiTestCase[]
}

/** Returns a build's test results, or null when it has none (e.g. it stopped before the tests). */
export async function getTestReport(pipeline: string, buildId: string): Promise<CiTestReport | null> {
  const res = await checkLogin(fetch(`/api/ci/builds/${encodeURIComponent(buildId)}/tests?${new URLSearchParams({pipeline})}`))
  if (res.status === 404) return null
  if (!res.ok) throw new Error(`GET /api/ci/builds/${buildId}/tests failed: ${res.status}`)
  return res.json()
}

export type CiBuilds = {
  pipeline: string
  builds: CiBuild[]
}

/** Returns the recent builds of a pipeline, newest first. */
export async function getBuilds(pipeline: string): Promise<CiBuilds> {
  const res = await checkLogin(fetch(`/api/ci/builds?${new URLSearchParams({pipeline})}`))
  if (!res.ok) throw new Error(`GET /api/ci/builds failed: ${res.status}`)
  return res.json()
}

/** One build, with who started it */
export type CiBuildDetails = {
  build: CiBuild
  /** The dashboard user when started through the dashboard, else the CI tool's user; null when no user did (push, timer) */
  startedBy: string | null
}

/** Returns one build of a pipeline. */
export async function getBuild(pipeline: string, buildId: string): Promise<CiBuildDetails> {
  const res = await checkLogin(fetch(`/api/ci/builds/${encodeURIComponent(buildId)}?${new URLSearchParams({pipeline})}`))
  if (!res.ok) throw new Error(`GET /api/ci/builds/${buildId} failed: ${res.status}`)
  return res.json()
}

/**
 * Starts a build of a pipeline. Returns the new build's id, or null when the CI tool
 * hasn't started it yet (still queued). Can take up to ~30 seconds.
 */
export async function triggerBuild(pipeline: string): Promise<string | null> {
  const res = await checkLogin(post(`/api/ci/builds?${new URLSearchParams({pipeline})}`))
  if (!res.ok) throw new Error(`POST /api/ci/builds failed: ${res.status}`)
  const data: { buildId: string | null } = await res.json()
  return data.buildId
}
