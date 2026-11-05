import {useCallback, useEffect, useMemo, useState, type ReactNode} from 'react'
import {useNavigate} from 'react-router'
import {getMe, logout as logoutRequest, type User} from '@/api/auth'
import {setUnauthorizedHandler} from '@/api/client'
import {AuthContext} from './AuthContext'
import LoginPage from './LoginPage'

/**
 * Who is logged in. Shows the login form instead of its children while nobody is, including when the session
 * ends mid-use (any request answered 401): the form then shows on the current page, so logging back in continues there.
 */
function AuthProvider({children}: { children: ReactNode }) {
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
      // Back to the landing page, so the next login starts there instead of on the page used before logging out
      navigate('/', {replace: true})
      setUser(null)
    },
  } : null, [user, navigate])

  if (user === undefined) return null

  if (!auth) {
    return (
      <LoginPage error={error} onLogin={() => {
        setError('')
        loadUser()
      }}/>
    )
  }

  return <AuthContext.Provider value={auth}>{children}</AuthContext.Provider>
}

export default AuthProvider
