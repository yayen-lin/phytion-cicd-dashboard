/** Returns what the backend says about itself, e.g. "ok". Needs no login. */
export async function getBackendStatus(): Promise<string> {
  const res = await fetch('/api/status')
  if (!res.ok) throw new Error(`GET /api/status failed: ${res.status}`)
  const data: { status: string } = await res.json()
  return data.status
}
