import {checkLogin} from './client'

export type App = {
  id: string
  repo: { tool: string, workspace: string, name: string }
  pipeline: { tool: string, job: string }
  release: { tool: string, image: string } | null
}

export async function getApps(): Promise<App[]> {
  const res = await checkLogin(fetch('/api/apps'))
  if (!res.ok) throw new Error(`GET /api/apps failed: ${res.status}`)
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
