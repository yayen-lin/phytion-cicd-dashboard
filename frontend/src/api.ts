export type User = {
  username: string
  roles: string[]
}

// The backend puts a CSRF token in the XSRF-TOKEN cookie; every POST must send it back
function csrfToken(): string | undefined {
  const cookie = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
  return cookie ? decodeURIComponent(cookie.split('=')[1]) : undefined
}

async function post(url: string, body?: URLSearchParams): Promise<Response> {
  // The cookie is gone after logout; any GET makes the backend set a new one
  if (!csrfToken()) await fetch('/api/status')
  return fetch(url, {
    method: 'POST',
    headers: {'X-XSRF-TOKEN': csrfToken() ?? ''},
    body,
  })
}

/** Returns the logged-in user, or null when nobody is logged in. */
export async function getMe(): Promise<User | null> {
  const res = await fetch('/api/me')
  if (res.status === 401) return null
  if (!res.ok) throw new Error(`GET /api/me failed: ${res.status}`)
  return res.json()
}

/** Returns true when the username and password are correct. */
export async function login(username: string, password: string): Promise<boolean> {
  const res = await post('/api/login', new URLSearchParams({username, password}))
  if (res.status === 401) return false
  if (!res.ok) throw new Error(`POST /api/login failed: ${res.status}`)
  return true
}

export async function logout(): Promise<void> {
  await post('/api/logout')
}

/** A CI job the dashboard shows, and the tool that runs it */
export type CiPipeline = {
  id: string // e.g. "jenkins:sample-app"
  tool: string // e.g. "jenkins"
  job: string // e.g. "sample-app"
}

/** Returns the configured CI pipelines, in the order the backend lists them. */
export async function getPipelines(): Promise<CiPipeline[]> {
  const res = await fetch('/api/ci/pipelines')
  if (!res.ok) throw new Error(`GET /api/ci/pipelines failed: ${res.status}`)
  return res.json()
}

export type CiBuildStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'UNSTABLE' | 'ABORTED' | 'UNKNOWN'

/** One build of a CI job */
export type CiBuild = {
  id: string
  status: CiBuildStatus
  commit: string | null
  branch: string | null
  startedAt: string
  durationMillis: number | null // null while running
  url: string
}

export type CiBuilds = {
  pipeline: string
  builds: CiBuild[]
}

/** Returns the recent builds of a pipeline, newest first. */
export async function getBuilds(pipeline: string): Promise<CiBuilds> {
  const res = await fetch(`/api/ci/builds?${new URLSearchParams({pipeline})}`)
  if (!res.ok) throw new Error(`GET /api/ci/builds failed: ${res.status}`)
  return res.json()
}

/**
 * Starts a build of a pipeline. Returns the new build's id, or null when the CI tool
 * hasn't started it yet (still queued). Can take up to ~30 seconds.
 */
export async function triggerBuild(pipeline: string): Promise<string | null> {
  const res = await post(`/api/ci/builds?${new URLSearchParams({pipeline})}`)
  if (!res.ok) throw new Error(`POST /api/ci/builds failed: ${res.status}`)
  const data: { buildId: string | null } = await res.json()
  return data.buildId
}

/** One action taken through the dashboard */
export type AuditEntry = {
  id: number
  username: string
  actionType: string
  target: string
  createdAt: string
  actionResult: 'SUCCESS' | 'FAILED' // whether the action worked, not whether the build passed
  buildId: string | null
  details: string | null
}

/** Returns the most recent audit log entries about a target (e.g. a pipeline id), newest first. */
export async function getAuditLog(target: string): Promise<AuditEntry[]> {
  const res = await fetch(`/api/audit?${new URLSearchParams({target})}`)
  if (!res.ok) throw new Error(`GET /api/audit failed: ${res.status}`)
  return res.json()
}
