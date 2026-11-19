import {checkLogin} from './client'
import type {EnvironmentApp} from './deploy'

/** A deploy build still running */
export type RunningDeploy = {
  appId: string
  version: string
  startedAt: string
  startedBy: string // who started it; the CI tool's name when no user did
  buildId: string
  url: string // the deploy build's page in the CI tool
}

/** The parts of an overview whose tool couldn't be reached */
export type EnvironmentOverviewPart = 'APPS'

/** One environment at a glance */
export type EnvironmentOverview = {
  environment: string
  apps: EnvironmentApp[] | null // the apps running there, failing first; null when the operate tool couldn't be reached
  runningDeploys: RunningDeploy[] // newest first
  unavailable: EnvironmentOverviewPart[]
}

/** Every environment at a glance, in the config's order, e.g. [{environment: "dev", apps: [sample-app 1.0.1], ...}]. */
export async function getOverview(): Promise<EnvironmentOverview[]> {
  const res = await checkLogin(fetch('/api/overview'))
  if (!res.ok) throw new Error(`GET /api/overview failed: ${res.status}`)
  const data: { environments: EnvironmentOverview[] } = await res.json()
  return data.environments
}
