import { checkLogin } from './client'

/** A code repo the dashboard shows */
export type CodeRepo = {
  id: string
  tool: string
  workspace: string
  name: string
}

/** Returns the configured code repos, in the order the backend lists them. */
export async function getRepos(): Promise<CodeRepo[]> {
  const res = await checkLogin(fetch('/api/code/repos'))
  if (!res.ok) throw new Error(`GET /api/code/repos failed: ${res.status}`)
  return res.json()
}

/** One commit in a repo */
export type CodeCommit = {
  hash: string
  message: string // the full message; the first line is the summary
  author: string | null
  committedAt: string
  url: string // the commit's page in the code tool
}

/** One branch of a repo */
export type CodeBranch = {
  name: string
  latestCommit: CodeCommit | null
  url: string // the branch's page in the code tool
}

/** Returns the branches of a repo, the most recently committed to first. */
export async function getBranches(repoId: string): Promise<{ repo: string, branches: CodeBranch[] }> {
  const res = await checkLogin(fetch(`/api/code/branches?${new URLSearchParams({ repoId })}`))
  if (!res.ok) throw new Error(`GET /api/code/branches failed: ${res.status}`)
  return res.json()
}

/** Returns the most recent commits on a branch, newest first. */
export async function getCommits(repoId: string, branch: string): Promise<{
  repo: string,
  branch: string,
  commits: CodeCommit[]
}> {
  const res = await checkLogin(fetch(`/api/code/commits?${new URLSearchParams({ repoId, branch })}`))
  if (!res.ok) throw new Error(`GET /api/code/commits failed: ${res.status}`)
  return res.json()
}
