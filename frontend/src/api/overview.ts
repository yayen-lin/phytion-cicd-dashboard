import { checkLogin } from './client'
import type { EnvironmentApp } from './environments'

export type RunningDeploy = {
  appId: string
  version: string
  startedAt: string
  startedBy: string // the CI tool's name when no user did
  buildId: string
  url: string
}

export type EnvironmentOverviewPart = 'APPS'

export type EnvironmentOverview = {
  environment: string
  apps: EnvironmentApp[] | null // failing first; null when the operate tool is down
  runningDeploys: RunningDeploy[]
  unavailable: EnvironmentOverviewPart[]
}

/** Returns every environment at a glance, in order. */
export async function getOverview(): Promise<EnvironmentOverview[]> {
  const res = await checkLogin(fetch('/api/overview'))
  if (!res.ok) throw new Error(`GET /api/overview failed: ${res.status}`)
  const data: { environments: EnvironmentOverview[] } = await res.json()
  return data.environments
}
