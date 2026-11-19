import {checkLogin, errorMessage, postJson, putJson} from './client'
import type {Role, User} from './auth'

export const roles: Role[] = ['VIEWER', 'DEVELOPER', 'ADMIN']

/** Every user, oldest first, e.g. [admin_andy (ADMIN), andy (DEVELOPER)]. Admin only. */
export async function getUsers(): Promise<User[]> {
  const res = await checkLogin(fetch('/api/users'))
  if (!res.ok) throw new Error(await errorMessage(res))
  return res.json()
}

/**
 * Adds an active user, e.g. ("sam", "password1", "DEVELOPER") -> sam.
 * Throws with the backend's reason when it refuses, e.g. the username is taken.
 */
export async function addUser(username: string, password: string, role: Role): Promise<User> {
  const res = await checkLogin(postJson('/api/users', {username, password, role}))
  if (!res.ok) throw new Error(await errorMessage(res))
  return res.json()
}

/** Gives a user another role; they are logged out. Throws with the backend's reason when it refuses. */
export async function changeRole(username: string, role: Role): Promise<User> {
  const res = await checkLogin(putJson(`/api/users/${encodeURIComponent(username)}/role`, {role}))
  if (!res.ok) throw new Error(await errorMessage(res))
  return res.json()
}

/** Deactivates (false, which logs them out) or reactivates (true) a user. Throws with the backend's reason when it refuses. */
export async function changeActive(username: string, active: boolean): Promise<User> {
  const res = await checkLogin(putJson(`/api/users/${encodeURIComponent(username)}/active`, {active}))
  if (!res.ok) throw new Error(await errorMessage(res))
  return res.json()
}
