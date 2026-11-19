import { checkLogin, post } from './client'

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
  tests: CiTestCounts | null // null when the build has no test results
}

export type CiTestCounts = {
  passed: number
  failed: number
  skipped: number
}

/** One test of a build */
export type CiTestCase = {
  className: string
  name: string
  status: 'PASSED' | 'FAILED' | 'SKIPPED'
  durationMillis: number
  errorMessage: string | null
}

export type CiTestReport = {
  counts: CiTestCounts
  durationMillis: number
  cases: CiTestCase[]
}

export type CiBuilds = {
  app: string
  builds: CiBuild[]
}

/** Returns the recent builds of an app's pipeline, newest first. */
export async function getBuilds(appId: string): Promise<CiBuilds> {
  const res = await checkLogin(fetch(`/api/ci/builds?${new URLSearchParams({ appId })}`))
  if (!res.ok) throw new Error(`GET /api/ci/builds failed: ${res.status}`)
  return res.json()
}

export type CiBuildDetails = {
  build: CiBuild
  /** The dashboard user, else the CI tool's user; null when no user started it */
  startedBy: string | null
  /** "dashboard" or the CI tool's id */
  startedBySource: string | null
  testReport: CiTestReport | null
}

/** Returns one build with who started it and its test results. */
export async function getBuild(appId: string, buildId: string): Promise<CiBuildDetails> {
  const res = await checkLogin(fetch(`/api/ci/builds/${encodeURIComponent(buildId)}?${new URLSearchParams({ appId })}`))
  if (!res.ok) throw new Error(`GET /api/ci/builds/${buildId} failed: ${res.status}`)
  return res.json()
}

/** Starts a build of an app's pipeline; returns its id, or null while it's still queued. */
export async function triggerBuild(appId: string): Promise<string | null> {
  const res = await checkLogin(post(`/api/ci/builds?${new URLSearchParams({ appId })}`))
  if (!res.ok) throw new Error(`POST /api/ci/builds failed: ${res.status}`)
  const data: { buildId: string | null } = await res.json()
  return data.buildId
}
