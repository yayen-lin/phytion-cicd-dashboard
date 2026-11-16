import {checkLogin} from './client'
import type {CiBuild} from './ci'
import type {EnvironmentApp} from './deploy'
import type {Environment} from './environments'
import type {ReleaseVersion} from './release'

export type App = {
  id: string
  repo: { tool: string, workspace: string, name: string }
  pipeline: { tool: string, job: string }
  release: { tool: string, image: string } | null
  deploy: { tool: string, job: string } | null
}

/** Returns one app's repo, pipeline, release and deploy, e.g. "sample-app" -> {id: "sample-app", ...}; null when unknown. */
export async function getApp(appId: string): Promise<App | null> {
  const res = await checkLogin(fetch(`/api/apps/${encodeURIComponent(appId)}`))
  if (res.status === 404) return null
  if (!res.ok) throw new Error(`GET /api/apps/${appId} failed: ${res.status}`)
  return res.json()
}

/** The parts of an app summary, each read from its own tool */
export type AppSummaryPart = 'BUILD' | 'VERSION' | 'ENVIRONMENTS'

/** One app at a glance. A part whose tool couldn't be reached is null (or empty) and listed in unavailable. */
export type AppSummary = {
  app: App
  latestBuild: CiBuild | null
  latestVersion: ReleaseVersion | null // null when there's none, or the app isn't released
  deployments: { environment: string, state: EnvironmentApp }[] // empty when the app isn't deployed
  unavailable: AppSummaryPart[]
}

/** Everything the Apps page shows: the environments, and one summary per app, in the config's order. */
export async function getAppsSummary(): Promise<{ environments: Environment[], apps: AppSummary[] }> {
  const res = await checkLogin(fetch('/api/apps/summary'))
  if (!res.ok) throw new Error(`GET /api/apps/summary failed: ${res.status}`)
  return res.json()
}

const displayNamesByToolId: Record<string, string> = {
  'bitbucket': 'Bitbucket',
  'github': 'GitHub',
  'jenkins': 'Jenkins',
  'github-actions': 'GitHub Actions',
  'docker-registry': 'Docker Registry',
  'ecr': 'AWS ECR',
}

export function toolIdToDisplayName(toolId: string): string {
  return displayNamesByToolId[toolId] ?? toolId
}

/** "Repo: andy-test-app/sample-app (Bitbucket)" */
export function appToRepoLabel(app: App): string {
  return `Repo: ${app.repo.workspace}/${app.repo.name} (${toolIdToDisplayName(app.repo.tool)})`
}

/** "Pipeline: sample-app (Jenkins)" */
export function appToPipelineLabel(app: App): string {
  return `Pipeline: ${app.pipeline.job} (${toolIdToDisplayName(app.pipeline.tool)})`
}

/** "Image: sample-app (Docker Registry)"; empty when the app isn't released */
export function appToImageLabel(app: App): string {
  return app.release ? `Image: ${app.release.image} (${toolIdToDisplayName(app.release.tool)})` : ''
}
