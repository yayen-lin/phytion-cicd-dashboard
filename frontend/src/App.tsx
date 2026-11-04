import {useEffect, useState} from 'react'
import {Navigate, Route, Routes} from 'react-router'
import {getMe, logout, type User} from './api'
import LoginPage from './LoginPage'
import AppLayout from './AppLayout'
import BuildPage from './BuildPage'

function App() {
  // undefined = still checking, null = not logged in
  const [user, setUser] = useState<User | null | undefined>(undefined)
  const [error, setError] = useState('')
  const [backendStatus, setBackendStatus] = useState('loading')

  function loadUser() {
    getMe()
      .then(setUser)
      .catch(() => {
        setUser(null)
        setError('Backend not reachable')
      })
  }

  useEffect(loadUser, [])

  useEffect(() => {
    if (!user) return
    fetch('/api/status')
      .then((res) => res.json())
      .then((data: { status: string }) => setBackendStatus(data.status))
      .catch(() => setBackendStatus('not reachable'))
  }, [user])

  async function handleLogout() {
    await logout()
    setUser(null)
  }

  if (user === undefined) return null

  if (user === null) {
    return (
      <LoginPage error={error} onLogin={() => {
        setError('')
        loadUser()
      }}/>
    )
  }

  return (
    <Routes>
      <Route element={<AppLayout user={user} backendStatus={backendStatus} onLogout={handleLogout}/>}>
        {/* Build is the only stage so far */}
        <Route index element={<Navigate to="/build/builds" replace/>}/>
        <Route path="build" element={<Navigate to="/build/builds" replace/>}/>
        {/* Each tab loads its own data each time it is opened */}
        <Route path="build/:tab" element={<BuildPage/>}/>
        <Route path="*" element={<Navigate to="/" replace/>}/>
      </Route>
    </Routes>
  )
}

export default App
