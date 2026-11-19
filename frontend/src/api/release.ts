import { checkLogin, errorMessage, postJson } from './client'
import type { CodeBranch, CodeCommit } from './code'

/** A released version: an image in the release tool; a field is null when the image lacks its label */
export type ReleaseVersion = {
  version: string // the image's tag, e.g. "1.4.0"
  buildId: string | null
  commit: string | null
  commitUrl: string | null
  createdAt: string | null
}

/** Returns the newest versions of an app, highest version first. */
export async function getVersions(appId: string): Promise<{ app: string, versions: ReleaseVersion[] }> {
  const res = await checkLogin(fetch(`/api/release/versions?${new URLSearchParams({ appId })}`))
  if (!res.ok) throw new Error(`GET /api/release/versions failed: ${res.status}`)
  return res.json()
}

export type ReleaseDraft = {
  app: string
  nextVersion: string // the highest tag with the last number bumped; "v0.0.1" for the first
  branches: CodeBranch[] | null // null when asked without commits
  branch: string | null
  commits: CodeCommit[] | null // null when asked without commits
}

/** Returns the next version and, when withCommits, the branches and one branch's commits. */
export async function getReleaseDraft(appId: string, branch: string | null, withCommits: boolean): Promise<ReleaseDraft> {
  const params = new URLSearchParams({ appId, withCommits: String(withCommits), ...(branch ? { branch } : {}) })
  const res = await checkLogin(fetch(`/api/release/draft?${params}`))
  if (!res.ok) throw new Error(`GET /api/release/draft failed: ${res.status}`)
  return res.json()
}

/** A version is a git tag like "v1.4.0" */
export const RELEASE_VERSION = /^v\d{1,9}\.\d{1,9}\.\d{1,9}$/

/**
 * Tags a commit as a version and starts its build; returns the build's id, or null while it's queued.
 * Throws with the backend's reason when it refuses.
 */
export async function release(appId: string, version: string, commit: string): Promise<string | null> {
  const res = await checkLogin(postJson('/api/release/versions', { appId, version, commit }))
  if (!res.ok) throw new Error(await errorMessage(res))
  const data: { buildId: string | null } = await res.json()
  return data.buildId
}
