export type BackendStatus = {
  status: string
  pollIntervalMillis: number
}

// used when the backend can't be asked
const DEFAULT_POLL_INTERVAL_MILLIS = 3000

/** Returns the backend's status and frontend settings; needs no login. */
export async function getBackendStatus(): Promise<BackendStatus> {
  const res = await fetch('/api/status')
  if (!res.ok) throw new Error(`GET /api/status failed: ${res.status}`)
  return res.json()
}

// asked once and shared; cleared on failure so the next call asks again
let pollIntervalRequest: Promise<number> | null = null

/** Returns how often a view with something running reloads; the default when the backend can't be asked. */
export function getPollIntervalMillis(): Promise<number> {
  pollIntervalRequest ??= getBackendStatus()
    .then((status) => status.pollIntervalMillis)
    .catch(() => {
      pollIntervalRequest = null
      return DEFAULT_POLL_INTERVAL_MILLIS
    })
  return pollIntervalRequest
}
