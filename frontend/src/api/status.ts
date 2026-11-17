/** What the backend says about itself, and the settings the frontend reads from it */
export type BackendStatus = {
  status: string // e.g. "ok"
  pollIntervalMillis: number // how often a view with something running loads again, e.g. 3000
}

// used when the backend can't be asked
const DEFAULT_POLL_INTERVAL_MILLIS = 3000

/** Returns what the backend says about itself, e.g. {status: "ok", pollIntervalMillis: 3000}. Needs no login. */
export async function getBackendStatus(): Promise<BackendStatus> {
  const res = await fetch('/api/status')
  if (!res.ok) throw new Error(`GET /api/status failed: ${res.status}`)
  return res.json()
}

// asked once and shared by every view that polls; forgotten when it fails, so the next view asks again
let pollIntervalRequest: Promise<number> | null = null

/**
 * Returns how often a view with something running loads again, from the backend's config, asked once.
 * e.g. 3000; 3000 when the backend can't be asked.
 */
export function getPollIntervalMillis(): Promise<number> {
  pollIntervalRequest ??= getBackendStatus()
    .then((status) => status.pollIntervalMillis)
    .catch(() => {
      pollIntervalRequest = null
      return DEFAULT_POLL_INTERVAL_MILLIS
    })
  return pollIntervalRequest
}
