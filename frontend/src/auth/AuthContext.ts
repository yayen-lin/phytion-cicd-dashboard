import { createContext, useContext } from 'react'
import type { User } from '@/api/auth'

export type Auth = {
  user: User
  /** Logs out and goes back to the landing page */
  logout: () => Promise<void>
}

export const AuthContext = createContext<Auth | null>(null)

/** Returns the logged-in user and logout; only works inside AuthProvider. */
export function useAuth(): Auth {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth must be used inside AuthProvider')
  return auth
}

/** What the user's role allows; the backend still checks every action */
export type Permissions = {
  /** run builds and releases */
  canAct: boolean
  isAdmin: boolean
  /** deploy, promote or roll back in an environment */
  canActIn: (environment: { needsAdmin: boolean }) => boolean
}

export function usePermissions(): Permissions {
  const { user } = useAuth()
  const canAct = user.role !== 'VIEWER'
  const isAdmin = user.role === 'ADMIN'
  return { canAct, isAdmin, canActIn: (environment) => canAct && (isAdmin || !environment.needsAdmin) }
}
