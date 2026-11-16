import {checkLogin} from './client'

/** A configured environment */
export type Environment = {
  name: string // e.g. "prod"
  needsConfirmation: boolean // whether a deploy there must be confirmed by typing the name
}

/** The configured environments, in the config's order, e.g. [{name: "dev", needsConfirmation: false}, ...]. */
export async function getConfiguredEnvironments(): Promise<Environment[]> {
  const res = await checkLogin(fetch('/api/environments'))
  if (!res.ok) throw new Error(`GET /api/environments failed: ${res.status}`)
  return res.json()
}
