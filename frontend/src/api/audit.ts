import {checkLogin} from './client'

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
  const res = await checkLogin(fetch(`/api/audit?${new URLSearchParams({target})}`))
  if (!res.ok) throw new Error(`GET /api/audit failed: ${res.status}`)
  return res.json()
}
