import { checkLogin, errorMessage, postJson } from './client'
import type { CiBuild } from './ci'

/**
 * Deploys a released version to an environment; returns the deploy build's id, or null while it's queued.
 * Throws with the backend's reason when it refuses.
 */
export async function deploy(appId: string, version: string, environment: string): Promise<string | null> {
  const res = await checkLogin(postJson('/api/deploy/deployments', { appId, version, environment }))
  if (!res.ok) throw new Error(await errorMessage(res))
  const data: { buildId: string | null } = await res.json()
  return data.buildId
}

export type Promotion = {
  appId: string
  version: string
  fromEnvironment: string
  toEnvironment: string
  buildId: string | null // null while queued
}

/**
 * Promotes the version running in an environment to the next one.
 * Throws with the backend's reason when it refuses.
 */
export async function promote(appId: string, fromEnvironment: string): Promise<Promotion> {
  const res = await checkLogin(postJson('/api/deploy/promotions', { appId, fromEnvironment }))
  if (!res.ok) throw new Error(await errorMessage(res))
  return res.json()
}

export type RollbackOptions = {
  appId: string
  environment: string
  fromVersion: string | null // null when nothing was deployed there
  toVersions: string[] // highest first; the first is the previous good one
}

export type Rollback = {
  appId: string
  environment: string
  fromVersion: string
  toVersion: string
  buildId: string | null // null while queued
}

/**
 * Returns the versions a rollback in an environment can go to.
 * Throws with the backend's reason when it refuses.
 */
export async function getRollbackOptions(appId: string, environment: string): Promise<RollbackOptions> {
  const res = await checkLogin(fetch(`/api/deploy/rollback-options?${new URLSearchParams({ appId, environment })}`))
  if (!res.ok) throw new Error(await errorMessage(res))
  return res.json()
}

/**
 * Rolls an app in an environment back to one of its rollback options.
 * Throws with the backend's reason when it refuses.
 */
export async function rollback(appId: string, environment: string, version: string): Promise<Rollback> {
  const res = await checkLogin(postJson('/api/deploy/rollbacks', { appId, environment, version }))
  if (!res.ok) throw new Error(await errorMessage(res))
  return res.json()
}

/** Returns one build of an app's deploy job; null when there's no such build. */
export async function getDeployBuild(appId: string, buildId: string): Promise<CiBuild | null> {
  const res = await checkLogin(fetch(`/api/deploy/deployments/${encodeURIComponent(buildId)}?${new URLSearchParams({ appId })}`))
  if (res.status === 404) return null
  if (!res.ok) throw new Error(`GET /api/deploy/deployments/${buildId} failed: ${res.status}`)
  return res.json()
}

export type DeployRun = {
  appId: string
  version: string
  environment: string
  build: CiBuild
  deployedBy: string // the CI tool's name when no user did
  action: 'DEPLOY' | 'PROMOTE' | 'ROLLBACK' // DEPLOY when started in the CI tool
}

export type DeployHistory = {
  appId: string
  environments: string[]
  deploys: {
    run: DeployRun
    commit: string | null
    commitUrl: string | null
  }[]
  unavailable: 'COMMITS'[]
}

/**
 * Returns an app's recent deploys to every environment, newest first.
 * Throws with the backend's reason when the CI tool can't be reached.
 */
export async function getDeployHistory(appId: string): Promise<DeployHistory> {
  const res = await checkLogin(fetch(`/api/deploy/deployments?${new URLSearchParams({ appId })}`))
  if (!res.ok) throw new Error(await errorMessage(res))
  return res.json()
}
