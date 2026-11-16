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

/** A repo's branches, with the commits on one of them */
export type BranchCommits = {
  app: string
  branches: CodeBranch[] // the most recently committed to first
  branch: string | null // the branch the commits are from; null when the repo has no branches
  commits: CodeCommit[] // newest first
}

/**
 * Returns the branches of an app's repo with the most recent commits on one of them: the asked branch, else main, else
 * master, else the most recently committed to.
 * e.g. ("sample-app", null) -> {branches: [main, dev], branch: "main", commits: [...]}; ("sample-app", "gone") -> main's
 */
export async function getCommits(appId: string, branch: string | null): Promise<BranchCommits> {
  const params = new URLSearchParams({appId, ...(branch ? {branch} : {})})
  const res = await checkLogin(fetch(`/api/code/commits?${params}`))
  if (!res.ok) throw new Error(`GET /api/code/commits failed: ${res.status}`)
  return res.json()
}
