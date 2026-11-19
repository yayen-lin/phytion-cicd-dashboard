import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { getMe, logout as logoutRequest, type User } from '@/api/auth'
import { setUnauthorizedHandler } from '@/api/client'
import { AuthContext } from './AuthContext'
import LoginPage from './LoginPage'

/** Shows the login form instead of its children while nobody is logged in, including after the session ends. */
function AuthProvider({ children }: { children: ReactNode }) {
  // undefined = still checking, null = not logged in
  const [user, setUser] = useState<User | null | undefined>(undefined)
  const [error, setError] = useState('')
  const navigate = useNavigate()

  const loadUser = useCallback(() => {
    getMe()
      .then(setUser)
      .catch(() => {
        setUser(null)
        setError('Backend not reachable')
      })
  }, [])

  useEffect(loadUser, [loadUser])

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setUser(null)
      setError('Your session has ended. Log in again to continue.')
    })
  }, [])

  const auth = useMemo(() => user ? {
    user,
    logout: async () => {
      await logoutRequest()
      // the next login starts on the landing page
      navigate('/', { replace: true })
      setUser(null)
    },
  } : null, [user, navigate])

  if (user === undefined) return null

  if (!auth) {
    return (
      <LoginPage error={error} onLogin={() => {
        setError('')
        loadUser()
      }} />
    )
  }

  return <AuthContext.Provider value={auth}>{children}</AuthContext.Provider>
}

export default AuthProvider
