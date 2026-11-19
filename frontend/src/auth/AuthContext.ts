import {createContext, useContext} from 'react'
import type {User} from '@/api/auth'

export type Auth = {
  /** The logged-in user */
  user: User
  /** Logs out and goes back to the landing page */
  logout: () => Promise<void>
}

export const AuthContext = createContext<Auth | null>(null)

/** The logged-in user and logout. Only works inside AuthProvider, which renders its children only when logged in. */
export function useAuth(): Auth {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth must be used inside AuthProvider')
  return auth
}

/** What the logged-in user's role allows. The backend refuses the rest, so this only hides what would be refused. */
export type Permissions = {
  /** run builds and releases: DEVELOPER and ADMIN */
  canAct: boolean
  /** manage users: ADMIN */
  isAdmin: boolean
  /** deploy, promote or roll back to an environment: e.g. a DEVELOPER and prod -> false */
  canActIn: (environment: { needsAdmin: boolean }) => boolean
}

export function usePermissions(): Permissions {
  const {user} = useAuth()
  const canAct = user.role !== 'VIEWER'
  const isAdmin = user.role === 'ADMIN'
  return {canAct, isAdmin, canActIn: (environment) => canAct && (isAdmin || !environment.needsAdmin)}
}
