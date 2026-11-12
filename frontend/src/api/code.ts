import { checkLogin } from './client'

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

/** Returns the branches of an app's repo, the most recently committed to first. */
export async function getBranches(appId: string): Promise<{ app: string, branches: CodeBranch[] }> {
  const res = await checkLogin(fetch(`/api/code/branches?${new URLSearchParams({ appId })}`))
  if (!res.ok) throw new Error(`GET /api/code/branches failed: ${res.status}`)
  return res.json()
}

/** Returns the most recent commits on a branch of an app's repo, newest first. */
export async function getCommits(appId: string, branch: string): Promise<{
  app: string,
  branch: string,
  commits: CodeCommit[]
}> {
  const res = await checkLogin(fetch(`/api/code/commits?${new URLSearchParams({ appId, branch })}`))
  if (!res.ok) throw new Error(`GET /api/code/commits failed: ${res.status}`)
  return res.json()
}
