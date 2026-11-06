import {checkLogin, errorMessage, postJson} from './client'

/** An app the Release page shows: its image in the release tool, the CI pipeline that releases it, and its code repo */
export type ReleaseApp = {
  id: string // e.g. "registry:sample-app"
  tool: string // e.g. "registry"
  image: string // e.g. "sample-app"
  pipeline: string // e.g. "jenkins:sample-app"
  repo: string // the code repo that releases are tagged in, e.g. "bitbucket:andy-test-app/sample-app"
}

/** Returns the configured apps, in the order the backend lists them. */
export async function getApps(): Promise<ReleaseApp[]> {
  const res = await checkLogin(fetch('/api/release/apps'))
  if (!res.ok) throw new Error(`GET /api/release/apps failed: ${res.status}`)
  return res.json()
}

/**
 * One released version of an app: an image in the release tool. Everything but the version comes from the image's
 * labels, so each is null when the image doesn't have that label.
 */
export type ReleaseVersion = {
  version: string // the image's tag, e.g. "1.4.0" (released from the git tag v1.4.0)
  buildId: string | null // the CI build that made it
  commit: string | null // the full hash of the commit it was built from
  commitUrl: string | null // that commit's page in the code tool
  createdAt: string | null // when it was released
}

/** Returns the newest versions of an app, highest version first. */
export async function getVersions(app: string): Promise<{ app: string, versions: ReleaseVersion[] }> {
  const res = await checkLogin(fetch(`/api/release/versions?${new URLSearchParams({app})}`))
  if (!res.ok) throw new Error(`GET /api/release/versions failed: ${res.status}`)
  return res.json()
}

/** A version's format: the git tag to create, e.g. "v1.4.0" (the image is then 1.4.0) */
export const RELEASE_VERSION = /^v\d{1,9}\.\d{1,9}\.\d{1,9}$/

/**
 * Releases a commit of an app as a version: the backend tags the commit and starts the tag's build, which pushes the
 * image. Returns the build's id, or null when the CI tool hasn't started it yet. Can take up to ~30 seconds.
 * Throws with the backend's reason when it refuses, e.g. the tag already exists.
 */
export async function release(app: string, version: string, commit: string): Promise<string | null> {
  const res = await checkLogin(postJson('/api/release/versions', {app, version, commit}))
  if (!res.ok) throw new Error(await errorMessage(res))
  const data: { buildId: string | null } = await res.json()
  return data.buildId
}
