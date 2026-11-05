import {checkLogin} from './client'

/** A code repo the dashboard shows, and the tool that hosts it */
export type CodeRepo = {
  id: string // e.g. "bitbucket:andy-test-app/sample-app"
  tool: string // e.g. "bitbucket"
  repo: string // e.g. "andy-test-app/sample-app"
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
export async function getBranches(repo: string): Promise<{ repo: string, branches: CodeBranch[] }> {
  const res = await checkLogin(fetch(`/api/code/branches?${new URLSearchParams({repo})}`))
  if (!res.ok) throw new Error(`GET /api/code/branches failed: ${res.status}`)
  return res.json()
}

/** Returns the most recent commits on a branch, newest first. */
export async function getCommits(repo: string, branch: string): Promise<{ repo: string, branch: string, commits: CodeCommit[] }> {
  const res = await checkLogin(fetch(`/api/code/commits?${new URLSearchParams({repo, branch})}`))
  if (!res.ok) throw new Error(`GET /api/code/commits failed: ${res.status}`)
  return res.json()
}
