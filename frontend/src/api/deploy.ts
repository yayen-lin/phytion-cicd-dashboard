import {checkLogin, errorMessage, postJson} from './client'
import type {CiBuild} from './ci'
import type {Environment} from './environments'

/** An app in an environment. When the app isn't deployed there, every field but appId is null. */
export type EnvironmentApp = {
  appId: string
  version: string | null // e.g. "1.0.1"
  commit: string | null // the full hash of the commit that version was built from
  commitUrl: string | null // that commit's page in the code tool
  desiredPods: number | null
  readyPods: number | null
  healthy: boolean | null // whether every desired pod is ready (false when scaled to 0)
  deployedAt: string | null // when the newest deploy build of this version here started, from the dashboard or the CI tool; null when none found
  deployedBy: string | null // the dashboard user, or the CI tool's user when started there
  deployBuildId: string | null // that deploy build in the app's deploy job
}

/** An environment and every deployable app in it, deployed or not */
export type EnvironmentStatus = {
  environment: string
  needsConfirmation: boolean // whether a deploy there must be confirmed by typing the name
  nextEnvironment: Environment | null // where a promote goes, e.g. staging -> prod; null for the last one
  apps: EnvironmentApp[]
}

export type EnvironmentFilter = {
  environment?: string
  appId?: string
}

/**
 * Returns what runs in each environment, read from the operate tool, optionally narrowed to one environment or app.
 * e.g. {appId: "sample-app"} -> [dev: sample-app 1.0.1, staging: sample-app not deployed, ...]
 * Throws with the backend's reason when it refuses, e.g. the environment isn't configured.
 */
export async function getEnvironments(filter: EnvironmentFilter = {}): Promise<EnvironmentStatus[]> {
  const params = new URLSearchParams(Object.entries(filter).filter(([, value]) => value) as [string, string][])
  const res = await checkLogin(fetch(`/api/deploy/environments?${params}`))
  if (!res.ok) throw new Error(await errorMessage(res))
  const data: { environments: EnvironmentStatus[] } = await res.json()
  return data.environments
}

/**
 * Deploys a released version of an app to an environment: the backend starts the app's deploy job.
 * Returns the deploy build's id, or null when the CI tool hasn't started it yet. Can take up to ~30 seconds.
 * Throws with the backend's reason when it refuses, e.g. the version isn't released.
 */
export async function deploy(appId: string, version: string, environment: string): Promise<string | null> {
  const res = await checkLogin(postJson('/api/deploy/deployments', {appId, version, environment}))
  if (!res.ok) throw new Error(await errorMessage(res))
  const data: { buildId: string | null } = await res.json()
  return data.buildId
}

/** A version moved from one environment to the next, e.g. sample-app 1.0.1 from staging to prod */
export type Promotion = {
  appId: string
  version: string // the version that ran in the source environment, e.g. "1.0.1"
  fromEnvironment: string
  toEnvironment: string
  buildId: string | null // the deploy build's id, or null when the CI tool hasn't started it yet
}

/**
 * Promotes the version running in an environment to the next one: the backend starts the app's deploy job.
 * e.g. ("sample-app", "staging") -> {version: "1.0.1", toEnvironment: "prod", buildId: "4", ...}. Can take up to ~30 seconds.
 * Throws with the backend's reason when it refuses, e.g. the next environment already runs that version.
 */
export async function promote(appId: string, fromEnvironment: string): Promise<Promotion> {
  const res = await checkLogin(postJson('/api/deploy/promotions', {appId, fromEnvironment}))
  if (!res.ok) throw new Error(await errorMessage(res))
  return res.json()
}

/** Returns one build of an app's deploy job, e.g. the one a deploy started; null when there's no such build. */
export async function getDeployBuild(appId: string, buildId: string): Promise<CiBuild | null> {
  const res = await checkLogin(fetch(`/api/deploy/deployments/${encodeURIComponent(buildId)}?${new URLSearchParams({appId})}`))
  if (res.status === 404) return null
  if (!res.ok) throw new Error(`GET /api/deploy/deployments/${buildId} failed: ${res.status}`)
  return res.json()
}
