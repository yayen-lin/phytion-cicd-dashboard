import {checkLogin} from './client'

export type AuditActionResult = 'SUCCESS' | 'FAILED'

/** One action taken through the dashboard */
export type AuditEntry = {
  id: number
  username: string
  actionType: string
  target: string
  createdAt: string
  actionResult: AuditActionResult // whether the action worked, not whether the build passed
  buildId: string | null
  details: string | null
}

export type AuditFilter = {
  target?: string
  actionType?: string
  username?: string
  actionResult?: AuditActionResult
}

/** Returns the most recent audit log entries that match the filter, newest first. */
export async function getAuditLog(filter: AuditFilter = {}): Promise<AuditEntry[]> {
  const params = new URLSearchParams(Object.entries(filter).filter(([, value]) => value) as [string, string][])
  const res = await checkLogin(fetch(`/api/audit?${params}`))
  if (!res.ok) throw new Error(`GET /api/audit failed: ${res.status}`)
  return res.json()
}
