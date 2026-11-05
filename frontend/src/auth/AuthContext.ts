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
