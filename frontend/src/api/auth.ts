import {post} from './client'

export type Role = 'VIEWER' | 'DEVELOPER' | 'ADMIN'

export type User = {
  username: string
  role: Role
  /** false once an admin deactivated the user */
  active: boolean
  /** when the user was added, ISO time */
  createdAt: string
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
