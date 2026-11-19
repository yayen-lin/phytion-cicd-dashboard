import { checkLogin, errorMessage } from './client'

export type Environment = {
  name: string
  needsConfirmation: boolean // a deploy there must be confirmed by typing its name
  needsAdmin: boolean // only an admin can act there
}

/** Returns the configured environments in order. */
export async function getConfiguredEnvironments(): Promise<Environment[]> {
  const res = await checkLogin(fetch('/api/environments'))
  if (!res.ok) throw new Error(`GET /api/environments failed: ${res.status}`)
  return res.json()
}

/** An app in an environment; every field but appId is null when it isn't deployed there */
export type EnvironmentApp = {
  appId: string
  version: string | null
  commit: string | null
  commitUrl: string | null
  desiredPods: number | null
  readyPods: number | null
  healthy: boolean | null // false when scaled to 0
  deployedAt: string | null
  deployedBy: string | null
  deployBuildId: string | null
}

export type EnvironmentStatus = {
  environment: string
  needsConfirmation: boolean
  needsAdmin: boolean
  nextEnvironment: Environment | null // null for the last one
  apps: EnvironmentApp[]
}

export type EnvironmentFilter = {
  environment?: string
  appId?: string
}

/**
 * Returns what runs in each environment, optionally narrowed to one environment or app.
 * Throws with the backend's reason when it refuses.
 */
export async function getEnvironments(filter: EnvironmentFilter = {}): Promise<EnvironmentStatus[]> {
  const params = new URLSearchParams(Object.entries(filter).filter(([, value]) => value) as [string, string][])
  const res = await checkLogin(fetch(`/api/deploy/environments?${params}`))
  if (!res.ok) throw new Error(await errorMessage(res))
  const data: { environments: EnvironmentStatus[] } = await res.json()
  return data.environments
}
