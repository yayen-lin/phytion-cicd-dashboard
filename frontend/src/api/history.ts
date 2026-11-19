import { checkLogin } from './client'
import type { CiBuild } from './ci'

export type AuditActionResult = 'SUCCESS' | 'FAILED'

export type AuditEntry = {
  id: number
  username: string
  actionType: string
  target: string
  createdAt: string
  actionResult: AuditActionResult // whether the action worked, not whether its build passed
  buildId: string | null
  details: string | null
  environment: string | null
  version: string | null
}

/** An action and the build it started; build is null when it isn't found */
export type HistoryEntry = {
  entry: AuditEntry
  build: CiBuild | null
}

export type History = {
  entries: HistoryEntry[]
  unavailable: 'BUILDS'[]
}

/** Returns the recent actions, newest first, each with its build. */
export async function getHistory(): Promise<History> {
  const res = await checkLogin(fetch('/api/history'))
  if (!res.ok) throw new Error(`GET /api/history failed: ${res.status}`)
  return res.json()
}
