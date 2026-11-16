import {checkLogin} from './client'
import type {CiBuild} from './ci'

export type AuditActionResult = 'SUCCESS' | 'FAILED'

/** One action taken through the dashboard */
export type AuditEntry = {
  id: number
  username: string
  actionType: string // e.g. "BUILD", "RELEASE", "DEPLOY"
  target: string // the app
  createdAt: string
  actionResult: AuditActionResult // whether the action worked, not whether the build passed
  buildId: string | null
  details: string | null
  environment: string | null // where a deploy went
  version: string | null // the version released or deployed
}

/**
 * One action and the build it started: from the app's pipeline for a build or release, its deploy job for a deploy.
 * build is null when it started none, the build is older than the recent builds, or the CI tool is down.
 */
export type HistoryEntry = {
  entry: AuditEntry
  build: CiBuild | null
}

/** The recent actions, newest first; 'BUILDS' in unavailable when the CI tool couldn't be reached */
export type History = {
  entries: HistoryEntry[]
  unavailable: 'BUILDS'[]
}

/** Returns the recent audit log with each entry's build, e.g. {entries: [{entry: DEPLOY sample-app, build: #3}], ...}. */
export async function getHistory(): Promise<History> {
  const res = await checkLogin(fetch('/api/history'))
  if (!res.ok) throw new Error(`GET /api/history failed: ${res.status}`)
  return res.json()
}
