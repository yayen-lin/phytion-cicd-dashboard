import { checkLogin } from './client'

export type CodeCommit = {
  hash: string
  message: string
  author: string | null
  committedAt: string
  url: string
}

export type CodeBranch = {
  name: string
  latestCommit: CodeCommit | null
  url: string
}

/** Returns an app's branches, most recently committed to first. */
export async function getBranches(appId: string): Promise<{ app: string, branches: CodeBranch[] }> {
  const res = await checkLogin(fetch(`/api/code/branches?${new URLSearchParams({ appId })}`))
  if (!res.ok) throw new Error(`GET /api/code/branches failed: ${res.status}`)
  return res.json()
}

export type BranchCommits = {
  app: string
  branches: CodeBranch[]
  branch: string | null // null when the repo has no branches
  commits: CodeCommit[]
}

/** Returns an app's branches and the latest commits on one branch; the default branch when it's missing or not given. */
export async function getCommits(appId: string, branch: string | null): Promise<BranchCommits> {
  const params = new URLSearchParams({ appId, ...(branch ? { branch } : {}) })
  const res = await checkLogin(fetch(`/api/code/commits?${params}`))
  if (!res.ok) throw new Error(`GET /api/code/commits failed: ${res.status}`)
  return res.json()
}
