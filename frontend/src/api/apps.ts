import { checkLogin } from './client'
import type { CiBuild } from './ci'
import type { Environment, EnvironmentApp } from './environments'
import type { ReleaseVersion } from './release'

export type App = {
  id: string
  repo: { tool: string, workspace: string, name: string }
  pipeline: { tool: string, job: string }
  release: { tool: string, image: string } | null
  deploy: { tool: string, job: string } | null
}

/** Returns an app's config; null when there's no such app. */
export async function getApp(appId: string): Promise<App | null> {
  const res = await checkLogin(fetch(`/api/apps/${encodeURIComponent(appId)}`))
  if (res.status === 404) return null
  if (!res.ok) throw new Error(`GET /api/apps/${appId} failed: ${res.status}`)
  return res.json()
}

/** The parts of an app summary, one per tool */
export type AppSummaryPart = 'BUILD' | 'VERSION' | 'ENVIRONMENTS'

/** One app at a glance; a part whose tool is down is empty and listed in unavailable */
export type AppSummary = {
  app: App
  latestBuild: CiBuild | null
  latestVersion: ReleaseVersion | null // null when not released yet
  deployments: { environment: string, state: EnvironmentApp }[]
  unavailable: AppSummaryPart[]
}

/** Returns the environments and a summary of every app. */
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

/** The app's repo and its tool, for a page header. */
export function appToRepoLabel(app: App): string {
  return `Repo: ${app.repo.workspace}/${app.repo.name} (${toolIdToDisplayName(app.repo.tool)})`
}

/** The app's pipeline and its tool. */
export function appToPipelineLabel(app: App): string {
  return `Pipeline: ${app.pipeline.job} (${toolIdToDisplayName(app.pipeline.tool)})`
}

/** The app's image and its tool; empty when not released. */
export function appToImageLabel(app: App): string {
  return app.release ? `Image: ${app.release.image} (${toolIdToDisplayName(app.release.tool)})` : ''
}
